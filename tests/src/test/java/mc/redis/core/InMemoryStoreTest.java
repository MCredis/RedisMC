package mc.redis.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStoreTest {

    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
    }

    @AfterEach
    void tearDown() {
        store.shutdown();
    }

    // -------------------------------------------------------------------------
    // Basic set / get / delete
    // -------------------------------------------------------------------------

    @Test
    void setAndGet() {
        store.set("key", "value");
        assertEquals("value", store.get("key").orElse(null));
    }

    @Test
    void getMissingKeyReturnsEmpty() {
        assertTrue(store.get("nonexistent").isEmpty());
    }

    @Test
    void setOverwritesPreviousValue() {
        store.set("key", "first");
        store.set("key", "second");
        assertEquals("second", store.get("key").orElse(null));
    }

    @Test
    void deleteExistingKeyReturnsTrue() {
        store.set("key", "value");
        assertTrue(store.delete("key"));
        assertTrue(store.get("key").isEmpty());
    }

    @Test
    void deleteMissingKeyReturnsFalse() {
        assertFalse(store.delete("nonexistent"));
    }

    // -------------------------------------------------------------------------
    // TTL expiry
    // -------------------------------------------------------------------------

    @Test
    void keyWithTtlExpiresAfterDelay() throws InterruptedException {
        store.set("temp", "data", 100, TimeUnit.MILLISECONDS);
        assertEquals("data", store.get("temp").orElse(null));

        Thread.sleep(200);
        assertTrue(store.get("temp").isEmpty());
    }

    @Test
    void keyWithTtlIsAccessibleBeforeExpiry() {
        store.set("temp", "data", 5, TimeUnit.SECONDS);
        assertEquals("data", store.get("temp").orElse(null));
    }

    // -------------------------------------------------------------------------
    // Increment / Decrement
    // -------------------------------------------------------------------------

    @Test
    void incrementMissingKeyStartsFromZero() {
        long result = store.increment("counter", 10);
        assertEquals(10, result);
    }

    @Test
    void incrementAccumulates() {
        store.increment("counter", 5);
        long result = store.increment("counter", 3);
        assertEquals(8, result);
    }

    @Test
    void decrementSubtracts() {
        store.set("counter", 100L);
        long result = store.decrement("counter", 30);
        assertEquals(70, result);
    }

    @Test
    void incrementNonNumericThrows() {
        store.set("key", "not a number");
        assertThrows(IllegalStateException.class, () -> store.increment("key", 1));
    }

    // -------------------------------------------------------------------------
    // Compare and Set
    // -------------------------------------------------------------------------

    @Test
    void compareAndSetSucceedsWhenExpectedMatches() {
        store.set("key", "old");
        assertTrue(store.compareAndSet("key", "old", "new"));
        assertEquals("new", store.get("key").orElse(null));
    }

    @Test
    void compareAndSetFailsWhenExpectedDiffers() {
        store.set("key", "old");
        assertFalse(store.compareAndSet("key", "wrong", "new"));
        assertEquals("old", store.get("key").orElse(null));
    }

    @Test
    void compareAndSetOnMissingKeyMatchesNull() {
        assertTrue(store.compareAndSet("key", null, "created"));
        assertEquals("created", store.get("key").orElse(null));
    }

    // -------------------------------------------------------------------------
    // Hash operations
    // -------------------------------------------------------------------------

    @Test
    void hsetAndHget() {
        store.hset("hash", "field1", "value1");
        store.hset("hash", "field2", "value2");
        assertEquals("value1", store.hget("hash", "field1").orElse(null));
        assertEquals("value2", store.hget("hash", "field2").orElse(null));
    }

    @Test
    void hgetMissingFieldReturnsEmpty() {
        store.hset("hash", "field1", "value1");
        assertTrue(store.hget("hash", "missing").isEmpty());
    }

    @Test
    void hgetMissingKeyReturnsEmpty() {
        assertTrue(store.hget("nokey", "field").isEmpty());
    }

    @Test
    void hsetWithTtlExpires() throws InterruptedException {
        store.hset("hash", "field", "value", 100, TimeUnit.MILLISECONDS);
        assertEquals("value", store.hget("hash", "field").orElse(null));

        Thread.sleep(200);
        assertTrue(store.hget("hash", "field").isEmpty());
    }

    @Test
    void hsetOnScalarKeyThrows() {
        store.set("scalar", "value");
        assertThrows(IllegalStateException.class, () -> store.hset("scalar", "field", "v"));
    }

    // -------------------------------------------------------------------------
    // Namespace isolation
    // -------------------------------------------------------------------------

    @Test
    void namespacesAreIsolated() {
        Namespace ns1 = store.namespace("ns1");
        Namespace ns2 = store.namespace("ns2");

        ns1.set("key", "from-ns1");
        ns2.set("key", "from-ns2");

        assertEquals("from-ns1", ns1.get("key").orElse(null));
        assertEquals("from-ns2", ns2.get("key").orElse(null));
    }

    @Test
    void namespaceDeleteDoesNotAffectOther() {
        Namespace ns1 = store.namespace("ns1");
        Namespace ns2 = store.namespace("ns2");

        ns1.set("key", "a");
        ns2.set("key", "b");

        ns1.delete("key");
        assertTrue(ns1.get("key").isEmpty());
        assertEquals("b", ns2.get("key").orElse(null));
    }

    @Test
    void namespaceIncrementWorks() {
        Namespace ns = store.namespace("game");
        ns.increment("score", 100);
        assertEquals(100L, ns.get("score").orElse(null));
    }

    // -------------------------------------------------------------------------
    // Concurrency
    // -------------------------------------------------------------------------

    @Test
    void concurrentIncrementsAreAtomic() throws InterruptedException {
        int threads = 10;
        int incrementsPerThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    store.increment("counter", 1);
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(threads * incrementsPerThread,
                ((Number) store.get("counter").orElse(0L)).longValue());
    }

    @Test
    void concurrentCompareAndSetIsConsistent() throws InterruptedException {
        store.set("cas", 0L);
        int threads = 10;
        int attemptsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicLong totalSuccesses = new AtomicLong();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                for (int j = 0; j < attemptsPerThread; j++) {
                    Optional<Object> current = store.get("cas");
                    long val = ((Number) current.orElse(0L)).longValue();
                    if (store.compareAndSet("cas", val, val + 1)) {
                        totalSuccesses.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        long finalValue = ((Number) store.get("cas").orElse(0L)).longValue();
        assertEquals(totalSuccesses.get(), finalValue);
    }
}
