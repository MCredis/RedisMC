package mc.redis.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletionException;

/**
 * Synchronous TCP client for the redisMC server.
 *
 * <p>Wraps {@link AsyncRedisMCClient} and exposes blocking methods that throw
 * {@link IOException} on failure — familiar to callers who prefer checked
 * exception handling over {@link java.util.concurrent.CompletableFuture}.</p>
 *
 * <p>For async usage, call {@link #async()} to access the underlying
 * {@link AsyncRedisMCClient} directly, or use {@link #namespace(String)} to
 * get a {@link RemoteNamespace} that offers both sync and async methods and
 * implements the core {@code KeyValueStore} interface.</p>
 *
 * <pre>{@code
 * try (RedisMCClient client = new RedisMCClient("localhost", 6380)) {
 *
 *     // Sync — blocking, throws IOException
 *     client.set("players", "steve:score", 1000L);
 *     long score = client.increment("players", "steve:score", 500);
 *
 *     // Via RemoteNamespace — implements KeyValueStore (same API as core)
 *     KeyValueStore players = client.namespace("players");
 *     players.set("steve:rank", "diamond");
 *
 *     // Async — non-blocking CompletableFuture
 *     client.async().getAsync("players", "steve:score")
 *           .thenAccept(v -> System.out.println("score: " + v.orElse("(none)")));
 * }
 * }</pre>
 */
public class RedisMCClient implements Closeable {

    private final AsyncRedisMCClient async;

    public RedisMCClient(String host, int port) throws IOException {
        this.async = new AsyncRedisMCClient(host, port);
    }

    // -------------------------------------------------------------------------
    // Namespace / async accessors
    // -------------------------------------------------------------------------

    /**
     * Returns a namespace-scoped view implementing {@code KeyValueStore} with
     * both sync and {@code ...Async()} methods.
     */
    public RemoteNamespace namespace(String name) {
        return async.namespace(name);
    }

    /** Returns the underlying async client for caller-controlled async usage. */
    public AsyncRedisMCClient async() {
        return async;
    }

    // -------------------------------------------------------------------------
    // Scalar operations (blocking, throws IOException)
    // -------------------------------------------------------------------------

    public void set(String namespace, String key, Object value) throws IOException {
        joinVoid(async.setAsync(namespace, key, value));
    }

    public void set(String namespace, String key, Object value, long ttlMillis) throws IOException {
        joinVoid(async.setAsync(namespace, key, value, ttlMillis));
    }

    public Optional<String> get(String namespace, String key) throws IOException {
        return joinGet(async.getAsync(namespace, key));
    }

    public boolean delete(String namespace, String key) throws IOException {
        return joinGet(async.deleteAsync(namespace, key));
    }

    // -------------------------------------------------------------------------
    // Atomic operations (blocking, throws IOException)
    // -------------------------------------------------------------------------

    public long increment(String namespace, String key, long amount) throws IOException {
        return joinGet(async.incrementAsync(namespace, key, amount));
    }

    public long decrement(String namespace, String key, long amount) throws IOException {
        return joinGet(async.decrementAsync(namespace, key, amount));
    }

    public boolean compareAndSet(String namespace, String key,
                                 Object expected, Object newValue) throws IOException {
        return joinGet(async.compareAndSetAsync(namespace, key, expected, newValue));
    }

    // -------------------------------------------------------------------------
    // Hash operations (blocking, throws IOException)
    // -------------------------------------------------------------------------

    public void hset(String namespace, String key, String field, Object value) throws IOException {
        joinVoid(async.hsetAsync(namespace, key, field, value));
    }

    public void hset(String namespace, String key, String field,
                     Object value, long ttlMillis) throws IOException {
        joinVoid(async.hsetAsync(namespace, key, field, value, ttlMillis));
    }

    public Optional<String> hget(String namespace, String key, String field) throws IOException {
        return joinGet(async.hgetAsync(namespace, key, field));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close() throws IOException {
        async.close();
    }

    // -------------------------------------------------------------------------
    // Helpers — unwrap CompletionException → IOException
    // -------------------------------------------------------------------------

    private static void joinVoid(java.util.concurrent.CompletableFuture<Void> future)
            throws IOException {
        try {
            future.join();
        } catch (CompletionException e) {
            rethrowIO(e);
        }
    }

    private static <T> T joinGet(java.util.concurrent.CompletableFuture<T> future)
            throws IOException {
        try {
            return future.join();
        } catch (CompletionException e) {
            rethrowIO(e);
            throw e; // unreachable
        }
    }

    private static void rethrowIO(CompletionException e) throws IOException {
        Throwable cause = e.getCause();
        if (cause instanceof IOException ioe) throw ioe;
        throw e;
    }
}
