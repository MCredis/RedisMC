package mc.redis.client;

import java.util.Optional;
import java.util.Set;

/**
 * Java API for interacting with a redisMC server.
 * Other applications and Minecraft plugins depend on this library.
 */
public class RedisMCClient {

    private final String host;
    private final int port;

    public RedisMCClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // TODO: open connection to server

    public void set(String namespace, String key, String value) {
        // TODO: send SET command to server
    }

    public void set(String namespace, String key, String value, long ttlMillis) {
        // TODO: send SET command with TTL to server
    }

    public Optional<String> get(String namespace, String key) {
        // TODO: send GET command to server
        return Optional.empty();
    }

    public boolean delete(String namespace, String key) {
        // TODO: send DEL command to server
        return false;
    }

    public boolean exists(String namespace, String key) {
        // TODO: send EXISTS command to server
        return false;
    }

    public Set<String> keys(String namespace) {
        // TODO: send KEYS command to server
        return Set.of();
    }

    public void close() {
        // TODO: close connection
    }
}
