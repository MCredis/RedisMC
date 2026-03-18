package mc.redis.core;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Snapshot-based persistence for {@link InMemoryStore}.
 *
 * <p>Periodically serializes the store contents to a binary file and
 * reloads them on startup. Uses atomic file replacement (write to temp,
 * then rename) to avoid corruption from partial writes.</p>
 *
 * <p>Only string and numeric scalar values are persisted. Hash values
 * (inner maps) are serialized as nested string-to-string maps. TTL
 * information is preserved — entries whose TTL has already elapsed at
 * load time are discarded.</p>
 *
 * <p>Call {@link #shutdown()} to perform a final save and release the
 * background scheduler.</p>
 */
public class SnapshotPersistence {

    private final InMemoryStore store;
    private final Path snapshotFile;
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a persistence layer that saves snapshots to the given file
     * at the specified interval.
     *
     * @param store        the store to persist
     * @param snapshotFile path to the snapshot file (e.g. {@code "redismc.snapshot"})
     * @param interval     how often to save
     * @param unit         time unit for the interval
     */
    public SnapshotPersistence(InMemoryStore store, Path snapshotFile,
                                long interval, TimeUnit unit) {
        this.store = store;
        this.snapshotFile = snapshotFile;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "redismc-snapshot");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::save, interval, interval, unit);
    }

    /**
     * Loads a previously saved snapshot into the store. Expired entries
     * are silently skipped. If the file does not exist, this is a no-op.
     */
    public void load() {
        if (!Files.exists(snapshotFile)) return;

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(snapshotFile)))) {

            int version = dis.readInt();
            if (version != 1) {
                throw new IOException("Unsupported snapshot version: " + version);
            }

            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                String key = dis.readUTF();
                long expiresAt = dis.readLong();
                byte type = dis.readByte();

                // Skip expired entries
                if (expiresAt != -1 && System.currentTimeMillis() >= expiresAt) {
                    skipValue(dis, type);
                    continue;
                }

                Object value = readValue(dis, type);
                if (expiresAt == -1) {
                    store.set(key, value);
                } else {
                    long remainingMs = expiresAt - System.currentTimeMillis();
                    if (remainingMs > 0) {
                        store.set(key, value, remainingMs, TimeUnit.MILLISECONDS);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load snapshot: " + e.getMessage());
        }
    }

    /** Saves the current store state to disk. */
    public void save() {
        try {
            Path tmpFile = snapshotFile.resolveSibling(snapshotFile.getFileName() + ".tmp");
            // Ensure parent directory exists
            Files.createDirectories(snapshotFile.getParent() != null
                    ? snapshotFile.getParent() : Path.of("."));

            Map<String, TtlEntry> snapshot = store.snapshot();

            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmpFile)))) {

                dos.writeInt(1); // version
                dos.writeInt(snapshot.size());

                for (Map.Entry<String, TtlEntry> entry : snapshot.entrySet()) {
                    TtlEntry ttl = entry.getValue();
                    if (ttl.isExpired()) continue;

                    dos.writeUTF(entry.getKey());
                    dos.writeLong(ttl.getExpiresAt());
                    writeValue(dos, ttl.getValue());
                }
            }

            Files.move(tmpFile, snapshotFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Failed to save snapshot: " + e.getMessage());
        }
    }

    /** Performs a final save and stops the background scheduler. */
    public void shutdown() {
        scheduler.shutdown();
        save();
    }

    // -------------------------------------------------------------------------
    // Serialization helpers
    // -------------------------------------------------------------------------

    private static final byte TYPE_STRING = 1;
    private static final byte TYPE_LONG = 2;
    private static final byte TYPE_HASH = 3;

    @SuppressWarnings("unchecked")
    private static void writeValue(DataOutputStream dos, Object value) throws IOException {
        if (value instanceof ConcurrentHashMap) {
            dos.writeByte(TYPE_HASH);
            Map<String, Object> map = (Map<String, Object>) value;
            dos.writeInt(map.size());
            for (Map.Entry<String, Object> e : map.entrySet()) {
                dos.writeUTF(e.getKey());
                dos.writeUTF(e.getValue().toString());
            }
        } else if (value instanceof Number) {
            dos.writeByte(TYPE_LONG);
            dos.writeLong(((Number) value).longValue());
        } else {
            dos.writeByte(TYPE_STRING);
            dos.writeUTF(value.toString());
        }
    }

    private static Object readValue(DataInputStream dis, byte type) throws IOException {
        return switch (type) {
            case TYPE_STRING -> dis.readUTF();
            case TYPE_LONG -> dis.readLong();
            case TYPE_HASH -> {
                int size = dis.readInt();
                ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
                for (int i = 0; i < size; i++) {
                    map.put(dis.readUTF(), dis.readUTF());
                }
                yield map;
            }
            default -> throw new IOException("Unknown value type: " + type);
        };
    }

    private static void skipValue(DataInputStream dis, byte type) throws IOException {
        switch (type) {
            case TYPE_STRING -> dis.readUTF();
            case TYPE_LONG -> dis.readLong();
            case TYPE_HASH -> {
                int size = dis.readInt();
                for (int i = 0; i < size; i++) {
                    dis.readUTF();
                    dis.readUTF();
                }
            }
            default -> throw new IOException("Unknown value type: " + type);
        }
    }
}
