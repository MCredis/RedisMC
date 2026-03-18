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
 * <p><b>Thread safety strategy:</b>
 * <ul>
 *   <li>Plain reads and writes use {@code ConcurrentHashMap}'s own segment-level
 *       locking — no global monitor needed.</li>
 *   <li>Compound operations (increment, decrement, compareAndSet, TTL set, hset)
 *       use {@link ConcurrentHashMap#compute} so the read-modify-write is atomic
 *       under the same per-bucket lock, without blocking the whole map.</li>
 *   <li>TTL expiry uses {@link ConcurrentHashMap#remove(Object, Object)}, which
 *       atomically removes the entry only when it still holds the exact reference
 *       captured at scheduling time — preventing a stale task from evicting a
 *       key that was overwritten before the TTL fired.</li>
 * </ul>
 *
 * <p><b>Hash storage layout:</b>
 * Hash values are stored as a {@link ConcurrentHashMap}{@code <String, Object>}
 * held inside a {@link TtlEntry}, exactly like any other value. The logical
 * structure is therefore {@code Map<String, Map<String, Object>>} — the outer
 * map is the main store keyed by the hash key, and the inner map holds the
 * fields. TTL is applied to the outer key, expiring all fields at once.</p>
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
        TtlEntry[] ref = new TtlEntry[1];
        store.compute(key, (k, old) -> {
            ref[0] = new TtlEntry(value, unit.toMillis(ttl));
            return ref[0];
        });
        scheduler.schedule(() -> store.remove(key, ref[0]), ttl, unit);
    }

    @Override
    public Optional<Object> get(String key) {
        Object[] result = new Object[1];
        store.compute(key, (k, entry) -> {
            if (entry == null)     return null;
            if (entry.isExpired()) return null;
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
    // Hash operations
    // -------------------------------------------------------------------------

    @Override
    public void hset(String key, String field, Object value) {
        store.compute(key, (k, entry) -> {
            if (entry == null || entry.isExpired()) {
                // Create a new hash; no TTL.
                ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
                map.put(field, value);
                return new TtlEntry(map);
            }
            // Mutate the existing inner map in-place so the outer TtlEntry
            // (and its TTL) is preserved unchanged.
            asHash(k, entry).put(field, value);
            return entry;
        });
    }

    @Override
    public void hset(String key, String field, Object value, long ttl, TimeUnit unit) {
        TtlEntry[] ref = new TtlEntry[1];
        store.compute(key, (k, entry) -> {
            // Carry over existing fields if the key already holds a live hash.
            ConcurrentHashMap<String, Object> map =
                    (entry != null && !entry.isExpired()) ? asHash(k, entry) : new ConcurrentHashMap<>();
            map.put(field, value);
            ref[0] = new TtlEntry(map, unit.toMillis(ttl));
            return ref[0];
        });
        scheduler.schedule(() -> store.remove(key, ref[0]), ttl, unit);
    }

    @Override
    public Optional<Object> hget(String key, String field) {
        Object[] result = new Object[1];
        store.compute(key, (k, entry) -> {
            if (entry == null || entry.isExpired()) return null;
            result[0] = asHash(k, entry).get(field);
            return entry;
        });
        return Optional.ofNullable(result[0]);
    }

    // -------------------------------------------------------------------------
    // Snapshot (for persistence)
    // -------------------------------------------------------------------------

    /**
     * Returns a shallow copy of the current store contents for persistence.
     * The returned map is a snapshot — mutations to it do not affect the store.
     */
    public java.util.Map<String, TtlEntry> snapshot() {
        return new java.util.HashMap<>(store);
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

    private static long numericValue(String key, TtlEntry entry) {
        if (entry == null || entry.isExpired()) return 0L;
        Object val = entry.getValue();
        if (!(val instanceof Number)) {
            throw new IllegalStateException(
                    "Key '" + key + "' holds a non-numeric value: " + val.getClass().getSimpleName());
        }
        return ((Number) val).longValue();
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> asHash(String key, TtlEntry entry) {
        Object val = entry.getValue();
        if (!(val instanceof ConcurrentHashMap)) {
            throw new IllegalStateException(
                    "Key '" + key + "' does not hold a hash; cannot use hset/hget on a scalar key");
        }
        return (ConcurrentHashMap<String, Object>) val;
    }
}
