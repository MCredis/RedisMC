package mc.redis.core;

/**
 * Wraps a stored value with an optional TTL (time-to-live) expiry timestamp.
 */
public class TtlEntry {

    private final String value;
    private final long expiresAt; // epoch millis, or -1 if no TTL

    public TtlEntry(String value) {
        this.value = value;
        this.expiresAt = -1;
    }

    public TtlEntry(String value, long ttlMillis) {
        this.value = value;
        this.expiresAt = System.currentTimeMillis() + ttlMillis;
    }

    public String getValue() {
        return value;
    }

    public boolean isExpired() {
        return expiresAt != -1 && System.currentTimeMillis() > expiresAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}
