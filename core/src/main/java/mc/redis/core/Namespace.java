package mc.redis.core;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A named partition of the key-value store.
 * Delegates to the parent {@link InMemoryStore}, prefixing every key with
 * {@code "<namespace>:<key>"} so namespaces never collide.
 */
public class Namespace implements KeyValueStore {

    private final String name;
    private final InMemoryStore store;

    Namespace(String name, InMemoryStore store) {
        this.name = name;
        this.store = store;
    }

    public String getName() {
        return name;
    }

    private String prefixed(String key) {
        return name + ":" + key;
    }

    @Override
    public void set(String key, Object value) {
        store.set(prefixed(key), value);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        store.set(prefixed(key), value, ttl, unit);
    }

    @Override
    public Optional<Object> get(String key) {
        return store.get(prefixed(key));
    }

    @Override
    public boolean delete(String key) {
        return store.delete(prefixed(key));
    }

    @Override
    public long increment(String key, long amount) {
        return store.increment(prefixed(key), amount);
    }

    @Override
    public long decrement(String key, long amount) {
        return store.decrement(prefixed(key), amount);
    }

    @Override
    public boolean compareAndSet(String key, Object expected, Object newValue) {
        return store.compareAndSet(prefixed(key), expected, newValue);
    }

    @Override
    public void hset(String key, String field, Object value) {
        store.hset(prefixed(key), field, value);
    }

    @Override
    public void hset(String key, String field, Object value, long ttl, TimeUnit unit) {
        store.hset(prefixed(key), field, value, ttl, unit);
    }

    @Override
    public Optional<Object> hget(String key, String field) {
        return store.hget(prefixed(key), field);
    }
}
