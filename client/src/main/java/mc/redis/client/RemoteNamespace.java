package mc.redis.client;

import mc.redis.core.KeyValueStore;

import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * A namespace-scoped remote client that implements {@link KeyValueStore},
 * making it a drop-in replacement for the in-memory
 * {@link mc.redis.core.Namespace}.
 *
 * <p>Sync methods block and throw {@link UncheckedIOException} on network
 * failure. For non-blocking usage, use the {@code ...Async()} methods which
 * return {@link CompletableFuture}.</p>
 */
public class RemoteNamespace implements KeyValueStore {

    private final String name;
    private final AsyncRedisMCClient async;

    RemoteNamespace(String name, AsyncRedisMCClient async) {
        this.name = name;
        this.async = async;
    }

    public String getName() {
        return name;
    }

    // -------------------------------------------------------------------------
    // Sync — implements KeyValueStore
    // -------------------------------------------------------------------------

    @Override
    public void set(String key, Object value) {
        join(async.setAsync(name, key, value));
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        join(async.setAsync(name, key, value, unit.toMillis(ttl)));
    }

    @Override
    public Optional<Object> get(String key) {
        Optional<String> result = join(async.getAsync(name, key));
        return result.map(s -> (Object) s);
    }

    @Override
    public boolean delete(String key) {
        return join(async.deleteAsync(name, key));
    }

    @Override
    public long increment(String key, long amount) {
        return join(async.incrementAsync(name, key, amount));
    }

    @Override
    public long decrement(String key, long amount) {
        return join(async.decrementAsync(name, key, amount));
    }

    @Override
    public boolean compareAndSet(String key, Object expected, Object newValue) {
        return join(async.compareAndSetAsync(name, key, expected, newValue));
    }

    @Override
    public void hset(String key, String field, Object value) {
        join(async.hsetAsync(name, key, field, value));
    }

    @Override
    public void hset(String key, String field, Object value, long ttl, TimeUnit unit) {
        join(async.hsetAsync(name, key, field, value, unit.toMillis(ttl)));
    }

    @Override
    public Optional<Object> hget(String key, String field) {
        Optional<String> result = join(async.hgetAsync(name, key, field));
        return result.map(s -> (Object) s);
    }

    // -------------------------------------------------------------------------
    // Async — non-blocking alternatives
    // -------------------------------------------------------------------------

    public CompletableFuture<Void> setAsync(String key, Object value) {
        return async.setAsync(name, key, value);
    }

    public CompletableFuture<Optional<String>> getAsync(String key) {
        return async.getAsync(name, key);
    }

    public CompletableFuture<Boolean> deleteAsync(String key) {
        return async.deleteAsync(name, key);
    }

    public CompletableFuture<Long> incrementAsync(String key, long amount) {
        return async.incrementAsync(name, key, amount);
    }

    public CompletableFuture<Long> decrementAsync(String key, long amount) {
        return async.decrementAsync(name, key, amount);
    }

    public CompletableFuture<Boolean> compareAndSetAsync(String key, Object expected, Object newValue) {
        return async.compareAndSetAsync(name, key, expected, newValue);
    }

    public CompletableFuture<Void> hsetAsync(String key, String field, Object value) {
        return async.hsetAsync(name, key, field, value);
    }

    public CompletableFuture<Optional<String>> hgetAsync(String key, String field) {
        return async.hgetAsync(name, key, field);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.io.IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
            throw e;
        }
    }
}
