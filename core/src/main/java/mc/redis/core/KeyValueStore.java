package mc.redis.core;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Core interface for key-value storage operations.
 */
public interface KeyValueStore {

    // -------------------------------------------------------------------------
    // String / scalar operations
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Hash / map operations
    // -------------------------------------------------------------------------

    /**
     * Sets {@code field} within the hash stored at {@code key}.
     * If no hash exists at {@code key} a new one is created.
     * Any existing TTL on the key is preserved.
     */
    void hset(String key, String field, Object value);

    /**
     * Sets {@code field} within the hash stored at {@code key} and (re)sets a
     * TTL on the entire hash key. The TTL applies to all fields in the hash.
     */
    void hset(String key, String field, Object value, long ttl, TimeUnit unit);

    /**
     * Returns the value of {@code field} within the hash stored at {@code key},
     * or {@link Optional#empty()} if the key or field does not exist (or has expired).
     */
    Optional<Object> hget(String key, String field);
}
