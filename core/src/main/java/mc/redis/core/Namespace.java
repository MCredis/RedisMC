package mc.redis.core;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A named partition of the key-value store.
 * Keys within a namespace are isolated from keys in other namespaces.
 */
public class Namespace implements KeyValueStore {

    private final String name;
    private final ConcurrentHashMap<String, TtlEntry> store = new ConcurrentHashMap<>();

    public Namespace(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void set(String key, String value) {
        store.put(key, new TtlEntry(value));
    }

    @Override
    public void set(String key, String value, long ttlMillis) {
        store.put(key, new TtlEntry(value, ttlMillis));
    }

    @Override
    public Optional<String> get(String key) {
        TtlEntry entry = store.get(key);
        if (entry == null) return Optional.empty();
        if (entry.isExpired()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.getValue());
    }

    @Override
    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    @Override
    public boolean exists(String key) {
        return get(key).isPresent();
    }

    @Override
    public Set<String> keys() {
        return store.entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public void clear() {
        store.clear();
    }
}
