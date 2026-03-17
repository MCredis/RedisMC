package mc.redis.core;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Core interface for key-value storage operations.
 */
public interface KeyValueStore {

    void set(String key, Object value);

    void set(String key, Object value, long ttl, TimeUnit unit);

    Optional<Object> get(String key);

    boolean delete(String key);
}
