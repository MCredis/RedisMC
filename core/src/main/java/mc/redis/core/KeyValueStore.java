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

    /**
     * Atomically adds {@code amount} to the numeric value at {@code key}.
     * If the key does not exist it is initialised to {@code 0} before adding.
     *
     * @return the value after the operation
     * @throws IllegalStateException if the existing value is not a {@link Number}
     */
    long increment(String key, long amount);

    /**
     * Atomically subtracts {@code amount} from the numeric value at {@code key}.
     * If the key does not exist it is initialised to {@code 0} before subtracting.
     *
     * @return the value after the operation
     * @throws IllegalStateException if the existing value is not a {@link Number}
     */
    long decrement(String key, long amount);

    /**
     * Atomically sets {@code key} to {@code newValue} if the current value
     * equals {@code expected} (using {@link Object#equals}).
     *
     * @return {@code true} if the swap was performed
     */
    boolean compareAndSet(String key, Object expected, Object newValue);
}
