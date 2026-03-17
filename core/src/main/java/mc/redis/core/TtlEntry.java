package mc.redis.core;

/**
 * Wraps a stored value with an optional TTL expiry timestamp.
 * {@code expiresAt == -1} means the entry never expires.
 */
class TtlEntry {

    private final Object value;
    private final long expiresAt; // epoch millis, or -1 for no TTL

    TtlEntry(Object value) {
        this.value = value;
        this.expiresAt = -1;
    }

    TtlEntry(Object value, long ttlMillis) {
        this.value = value;
        this.expiresAt = System.currentTimeMillis() + ttlMillis;
    }

    Object getValue() {
        return value;
    }

    boolean isExpired() {
        return expiresAt != -1 && System.currentTimeMillis() >= expiresAt;
    }
}
