package mc.redis.core;

import java.util.Optional;

/**
 * Core interface for key-value storage operations.
 */
public interface KeyValueStore {

    void set(String key, Object value);

    Optional<Object> get(String key);

    boolean delete(String key);
}
