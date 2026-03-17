package mc.redis.core;

import java.util.Optional;

/**
 * A named partition of the key-value store.
 * Not yet implemented — placeholder for future namespace support.
 */
public class Namespace implements KeyValueStore {

    private final String name;

    public Namespace(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void set(String key, Object value) {
        // TODO: implement namespace-scoped storage
    }

    @Override
    public Optional<Object> get(String key) {
        // TODO: implement namespace-scoped retrieval
        return Optional.empty();
    }

    @Override
    public boolean delete(String key) {
        // TODO: implement namespace-scoped deletion
        return false;
    }
}
