package mc.redis.core;

import java.util.Optional;

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
    public Optional<Object> get(String key) {
        return store.get(prefixed(key));
    }

    @Override
    public boolean delete(String key) {
        return store.delete(prefixed(key));
    }
}
