package mc.redis.client;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous TCP client for the redisMC server.
 *
 * <p>All operations return {@link CompletableFuture}s that complete on a
 * dedicated I/O thread. The underlying TCP connection is <b>not</b>
 * multiplexed — each {@code ...Async} call writes a request and reads the
 * response sequentially. Callers that need high throughput should open
 * multiple {@code AsyncRedisMCClient} instances.</p>
 */
public class AsyncRedisMCClient implements Closeable {

    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;
    private final ExecutorService ioExecutor;
    private final Object writeLock = new Object();

    public AsyncRedisMCClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "redismc-async-io");
            t.setDaemon(true);
            return t;
        });
    }

    /** Returns a namespace-scoped view with both sync and async methods. */
    public RemoteNamespace namespace(String name) {
        return new RemoteNamespace(name, this);
    }

    // -------------------------------------------------------------------------
    // Scalar operations
    // -------------------------------------------------------------------------

    public CompletableFuture<Void> setAsync(String namespace, String key, Object value) {
        String json = "{\"cmd\":\"SET\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key)
                + "\",\"value\":" + jsonValue(value) + "}";
        return sendVoid(json);
    }

    public CompletableFuture<Void> setAsync(String namespace, String key, Object value, long ttlMillis) {
        String json = "{\"cmd\":\"SET\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key)
                + "\",\"value\":" + jsonValue(value)
                + ",\"ttl\":\"" + ttlMillis + "\"}";
        return sendVoid(json);
    }

    public CompletableFuture<Optional<String>> getAsync(String namespace, String key) {
        String json = "{\"cmd\":\"GET\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key) + "\"}";
        return sendAndExtractValue(json);
    }

    public CompletableFuture<Boolean> deleteAsync(String namespace, String key) {
        String json = "{\"cmd\":\"DELETE\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key) + "\"}";
        return send(json).thenApply(resp -> "true".equals(extractValue(resp)));
    }

    // -------------------------------------------------------------------------
    // Atomic operations
    // -------------------------------------------------------------------------

    public CompletableFuture<Long> incrementAsync(String namespace, String key, long amount) {
        String json = "{\"cmd\":\"INCR\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key)
                + "\",\"amount\":\"" + amount + "\"}";
        return send(json).thenApply(resp -> Long.parseLong(extractValue(resp)));
    }

    public CompletableFuture<Long> decrementAsync(String namespace, String key, long amount) {
        String json = "{\"cmd\":\"DECR\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key)
                + "\",\"amount\":\"" + amount + "\"}";
        return send(json).thenApply(resp -> Long.parseLong(extractValue(resp)));
    }

    public CompletableFuture<Boolean> compareAndSetAsync(String namespace, String key,
                                                          Object expected, Object newValue) {
        String json = "{\"cmd\":\"CAS\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key)
                + "\",\"expected\":" + jsonValue(expected)
                + ",\"newValue\":" + jsonValue(newValue) + "}";
        return send(json).thenApply(resp -> "true".equals(extractValue(resp)));
    }

    // -------------------------------------------------------------------------
    // Hash operations
    // -------------------------------------------------------------------------

    public CompletableFuture<Void> hsetAsync(String namespace, String key,
                                              String field, Object value) {
        String json = "{\"cmd\":\"HSET\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key)
                + "\",\"field\":\"" + esc(field)
                + "\",\"value\":" + jsonValue(value) + "}";
        return sendVoid(json);
    }

    public CompletableFuture<Void> hsetAsync(String namespace, String key,
                                              String field, Object value, long ttlMillis) {
        String json = "{\"cmd\":\"HSET\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key)
                + "\",\"field\":\"" + esc(field)
                + "\",\"value\":" + jsonValue(value)
                + ",\"ttl\":\"" + ttlMillis + "\"}";
        return sendVoid(json);
    }

    public CompletableFuture<Optional<String>> hgetAsync(String namespace, String key, String field) {
        String json = "{\"cmd\":\"HGET\",\"namespace\":\"" + esc(namespace)
                + "\",\"key\":\"" + esc(key)
                + "\",\"field\":\"" + esc(field) + "\"}";
        return sendAndExtractValue(json);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close() throws IOException {
        ioExecutor.shutdown();
        out.close();
        in.close();
        socket.close();
    }

    // -------------------------------------------------------------------------
    // Internal I/O
    // -------------------------------------------------------------------------

    private CompletableFuture<String> send(String json) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (writeLock) {
                try {
                    out.println(json);
                    String response = in.readLine();
                    if (response == null) {
                        throw new IOException("Server closed the connection");
                    }
                    if (response.contains("\"ERROR\"")) {
                        String msg = extractField(response, "message");
                        throw new IOException("Server error: " + (msg != null ? msg : response));
                    }
                    return response;
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
        }, ioExecutor);
    }

    private CompletableFuture<Void> sendVoid(String json) {
        return send(json).thenApply(ignored -> null);
    }

    private CompletableFuture<Optional<String>> sendAndExtractValue(String json) {
        return send(json).thenApply(resp -> {
            String val = extractValue(resp);
            return Optional.ofNullable(val);
        });
    }

    private static String extractValue(String json) {
        return extractField(json, "value");
    }

    private static String extractField(String json, String field) {
        String pattern = "\"" + field + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int start = idx + pattern.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        if (json.substring(start).startsWith("null")) return null;

        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            if (end == -1) return null;
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }

    private static String jsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number) return v.toString();
        return "\"" + esc(v.toString()) + "\"";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
