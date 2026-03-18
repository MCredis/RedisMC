package mc.redis.server;

import mc.redis.core.InMemoryStore;
import mc.redis.core.Namespace;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles a single client TCP connection. Reads one JSON command per line,
 * dispatches to the {@link InMemoryStore}, and writes back a JSON response.
 *
 * <p>Protocol: newline-delimited JSON. Each request is a JSON object with
 * at least a {@code "cmd"} field. Responses are JSON objects with a
 * {@code "status"} field ({@code "OK"} or {@code "ERROR"}) and an optional
 * {@code "value"} field.</p>
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final InMemoryStore store;
    private final String requiredPassword; // null = no auth required
    private final ConcurrentHashMap<String, Namespace> namespaces = new ConcurrentHashMap<>();
    private boolean authenticated;

    public ClientHandler(Socket socket, InMemoryStore store) {
        this(socket, store, null);
    }

    public ClientHandler(Socket socket, InMemoryStore store, String password) {
        this.socket = socket;
        this.store = store;
        this.requiredPassword = password;
        this.authenticated = (password == null); // no password = auto-authenticated
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String response = handleCommand(line.trim());
                out.println(response);
            }
        } catch (IOException e) {
            // Client disconnected — normal.
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private String handleCommand(String json) {
        try {
            String cmd = extractString(json, "cmd");

            // Handle AUTH before anything else
            if ("AUTH".equalsIgnoreCase(cmd)) {
                String password = extractString(json, "password");
                if (requiredPassword == null) {
                    authenticated = true;
                    return "{\"status\":\"OK\",\"message\":\"No password required\"}";
                }
                if (requiredPassword.equals(password)) {
                    authenticated = true;
                    return "{\"status\":\"OK\"}";
                }
                return "{\"status\":\"ERROR\",\"message\":\"Invalid password\"}";
            }

            if (!authenticated) {
                return "{\"status\":\"ERROR\",\"message\":\"Authentication required. Send AUTH command first.\"}";
            }

            String ns = extractString(json, "namespace");
            String key = extractString(json, "key");

            Namespace namespace = namespaces.computeIfAbsent(ns, store::namespace);

            return switch (cmd.toUpperCase()) {
                case "SET" -> {
                    String value = extractString(json, "value");
                    String ttl = extractStringOrNull(json, "ttl");
                    if (ttl != null) {
                        namespace.set(key, value, Long.parseLong(ttl), TimeUnit.MILLISECONDS);
                    } else {
                        namespace.set(key, value);
                    }
                    yield "{\"status\":\"OK\"}";
                }
                case "GET" -> {
                    Optional<Object> val = namespace.get(key);
                    yield val.map(v -> "{\"status\":\"OK\",\"value\":" + jsonValue(v) + "}")
                            .orElse("{\"status\":\"OK\",\"value\":null}");
                }
                case "DELETE" -> {
                    boolean deleted = namespace.delete(key);
                    yield "{\"status\":\"OK\",\"value\":" + deleted + "}";
                }
                case "INCR" -> {
                    long amount = Long.parseLong(extractString(json, "amount"));
                    long result = namespace.increment(key, amount);
                    yield "{\"status\":\"OK\",\"value\":" + result + "}";
                }
                case "DECR" -> {
                    long amount = Long.parseLong(extractString(json, "amount"));
                    long result = namespace.decrement(key, amount);
                    yield "{\"status\":\"OK\",\"value\":" + result + "}";
                }
                case "CAS" -> {
                    String expected = extractStringOrNull(json, "expected");
                    String newValue = extractString(json, "newValue");
                    boolean swapped = namespace.compareAndSet(key, expected, newValue);
                    yield "{\"status\":\"OK\",\"value\":" + swapped + "}";
                }
                case "HSET" -> {
                    String field = extractString(json, "field");
                    String value = extractString(json, "value");
                    String ttl = extractStringOrNull(json, "ttl");
                    if (ttl != null) {
                        namespace.hset(key, field, value, Long.parseLong(ttl), TimeUnit.MILLISECONDS);
                    } else {
                        namespace.hset(key, field, value);
                    }
                    yield "{\"status\":\"OK\"}";
                }
                case "HGET" -> {
                    String field = extractString(json, "field");
                    Optional<Object> val = namespace.hget(key, field);
                    yield val.map(v -> "{\"status\":\"OK\",\"value\":" + jsonValue(v) + "}")
                            .orElse("{\"status\":\"OK\",\"value\":null}");
                }
                default -> "{\"status\":\"ERROR\",\"message\":\"Unknown command: " + cmd + "\"}";
            };
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    // -------------------------------------------------------------------------
    // Minimal JSON helpers (no external library)
    // -------------------------------------------------------------------------

    private static String extractString(String json, String field) {
        String value = extractStringOrNull(json, field);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static String extractStringOrNull(String json, String field) {
        String pattern = "\"" + field + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int start = idx + pattern.length();
        // Skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;

        if (json.charAt(start) == '"') {
            // Quoted string value
            int end = json.indexOf('"', start + 1);
            if (end == -1) return null;
            return json.substring(start + 1, end);
        } else if (json.substring(start).startsWith("null")) {
            return null;
        } else {
            // Unquoted value (number, boolean)
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }

    private static String jsonValue(Object v) {
        if (v instanceof Number) return v.toString();
        return "\"" + escapeJson(v.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
