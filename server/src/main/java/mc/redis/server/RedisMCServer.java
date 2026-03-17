package mc.redis.server;

import mc.redis.core.InMemoryStore;
import mc.redis.core.Namespace;

import java.util.concurrent.TimeUnit;

/**
 * Entry point for the redisMC standalone server process.
 */
public class RedisMCServer {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("redisMC server starting...");

        InMemoryStore store = new InMemoryStore();
        Namespace players   = store.namespace("players");
        Namespace cooldowns = store.namespace("cooldowns");

        // --- increment / decrement -------------------------------------------
        players.set("steve:score", 1000L);

        long after1 = players.increment("steve:score", 500);
        System.out.println("after +500  -> " + after1);          // 1500

        long after2 = players.decrement("steve:score", 200);
        System.out.println("after -200  -> " + after2);          // 1300

        // increment on a missing key treats it as 0
        long after3 = players.increment("alex:score", 300);
        System.out.println("alex +300 (from 0) -> " + after3);   // 300

        // --- compareAndSet ---------------------------------------------------
        // Swap succeeds: current == 1300
        boolean swapped = players.compareAndSet("steve:score", 1300L, 9999L);
        System.out.println("CAS 1300->9999 swapped: " + swapped);                  // true
        players.get("steve:score").ifPresent(v -> System.out.println("score now -> " + v)); // 9999

        // Swap fails: current is 9999, not 1300
        boolean notSwapped = players.compareAndSet("steve:score", 1300L, 0L);
        System.out.println("CAS 1300->0 (stale) swapped: " + notSwapped);          // false
        players.get("steve:score").ifPresent(v -> System.out.println("score still -> " + v)); // 9999

        // --- TTL + atomic interplay ------------------------------------------
        // Set a cooldown with TTL, then verify it expires
        cooldowns.set("steve:home", true, 2, TimeUnit.SECONDS);
        System.out.println("\n[t=0s] steve:home -> " + cooldowns.get("steve:home")); // Optional[true]

        Thread.sleep(3_000);

        System.out.println("[t=3s] steve:home -> " + cooldowns.get("steve:home")); // Optional.empty

        // Score is a persistent key — unaffected by TTL expiry
        System.out.println("[t=3s] steve:score -> " + players.get("steve:score")); // Optional[9999]

        store.shutdown();

        // TODO: parse config, bind network socket, start request loop
    }
}
