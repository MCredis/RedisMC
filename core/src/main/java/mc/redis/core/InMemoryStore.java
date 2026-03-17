package mc.redis.core;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory implementation of {@link KeyValueStore} backed by a
 * {@link ConcurrentHashMap}.
 *
 * <p>Thread safety strategy:
 * <ul>
 *   <li>Plain reads and writes use {@code ConcurrentHashMap}'s own segment-level
 *       locking — no global monitor needed.</li>
 *   <li>Compound operations (increment, decrement, compareAndSet, TTL set) use
 *       {@link ConcurrentHashMap#compute} so the read-modify-write is atomic
 *       under the same per-bucket lock, without blocking the whole map.</li>
 *   <li>TTL expiry uses {@link ConcurrentHashMap#remove(Object, Object)}, which
 *       atomically removes the entry only when it still holds the exact reference
 *       captured at scheduling time — preventing a stale task from evicting a
 *       key that was overwritten before the TTL fired.</li>
 * </ul>
 * </p>
 *
 * <p>Call {@link #shutdown()} when the store is no longer needed to release
 * the background scheduler thread.</p>
 */
public class InMemoryStore implements KeyValueStore {

    private final ConcurrentHashMap<String, TtlEntry> store = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "redismc-ttl-cleanup");
                t.setDaemon(true);
                return t;
            });

    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    @Override
    public void set(String key, Object value) {
        store.put(key, new TtlEntry(value));
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        // compute() atomically stores the entry and gives us the reference in
        // one step, so the scheduler always captures the correct TtlEntry even
        // if another thread is concurrently writing the same key.
        TtlEntry[] ref = new TtlEntry[1];
        store.compute(key, (k, old) -> {
            ref[0] = new TtlEntry(value, unit.toMillis(ttl));
            return ref[0];
        });
        scheduler.schedule(() -> store.remove(key, ref[0]), ttl, unit);
    }

    @Override
    public Optional<Object> get(String key) {
        // compute() makes the expired-check-then-remove atomic, so two
        // concurrent gets on an expired key can't both see the value.
        Object[] result = new Object[1];
        store.compute(key, (k, entry) -> {
            if (entry == null)        return null;
            if (entry.isExpired())    return null; // removes the entry
            result[0] = entry.getValue();
            return entry;
        });
        return Optional.ofNullable(result[0]);
    }

    @Override
    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    // -------------------------------------------------------------------------
    // Atomic operations
    // -------------------------------------------------------------------------

    @Override
    public long increment(String key, long amount) {
        long[] result = new long[1];
        store.compute(key, (k, entry) -> {
            long current = numericValue(k, entry);
            result[0] = current + amount;
            return new TtlEntry(result[0]);
        });
        return result[0];
    }

    @Override
    public long decrement(String key, long amount) {
        return increment(key, -amount);
    }

    @Override
    public boolean compareAndSet(String key, Object expected, Object newValue) {
        boolean[] swapped = new boolean[1];
        store.compute(key, (k, entry) -> {
            Object current = (entry == null || entry.isExpired()) ? null : entry.getValue();
            if (Objects.equals(current, expected)) {
                swapped[0] = true;
                return new TtlEntry(newValue);
            }
            return entry;
        });
        return swapped[0];
    }

    // -------------------------------------------------------------------------
    // Namespace factory
    // -------------------------------------------------------------------------

    public Namespace namespace(String name) {
        return new Namespace(name, this);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the current long value from an entry, treating absent or expired
     * entries as {@code 0}. Throws if the stored value is not a {@link Number}.
     */
    private static long numericValue(String key, TtlEntry entry) {
        if (entry == null || entry.isExpired()) return 0L;
        Object val = entry.getValue();
        if (!(val instanceof Number)) {
            throw new IllegalStateException(
                    "Key '" + key + "' holds a non-numeric value: " + val.getClass().getSimpleName());
        }
        return ((Number) val).longValue();
    }
}
