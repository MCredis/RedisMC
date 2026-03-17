package mc.redis.core;

import java.util.Optional;
import java.util.Set;

/**
 * Core interface for key-value storage operations.
 */
public interface KeyValueStore {

    void set(String key, String value);

    void set(String key, String value, long ttlMillis);

    Optional<String> get(String key);

    boolean delete(String key);

    boolean exists(String key);

    Set<String> keys();

    void clear();
}
