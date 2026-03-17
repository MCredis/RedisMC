package mc.redis.client;

import mc.redis.core.KeyValueStore;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates the three usage styles of the redisMC client module.
 *
 * <p>Requires a running redisMC server — start {@code mc.redis.server.RedisMCServer}
 * before running this class.</p>
 */
public class RedisMCClientExample {

    public static void main(String[] args) throws IOException, InterruptedException {
        try (RedisMCClient client = new RedisMCClient("localhost", 6380)) {

            demoSync(client);
            demoKeyValueStoreApi(client);
            demoAsync(client);
        }
    }

    // -------------------------------------------------------------------------
    // 1. Sync API via RedisMCClient — familiar, blocking, throws IOException
    // -------------------------------------------------------------------------

    static void demoSync(RedisMCClient client) throws IOException {
        System.out.println("=== Sync API ===");

        client.set("players", "steve:motd", "Hello world");
        Optional<String> motd = client.get("players", "steve:motd");
        System.out.println("GET  steve:motd       -> " + motd.orElse("(empty)"));

        long score = client.increment("players", "steve:score", 500);
        System.out.println("INCR steve:score +500 -> " + score);           // 500

        score = client.decrement("players", "steve:score", 100);
        System.out.println("DECR steve:score -100 -> " + score);           // 400

        // CAS: swap succeeds because current value is 400
        boolean swapped = client.compareAndSet("players", "steve:score", 400L, 1000L);
        System.out.println("CAS  400 -> 1000      -> swapped=" + swapped); // true
        System.out.println("GET  steve:score      -> " + client.get("players", "steve:score").orElse("(empty)"));

        client.hset("players", "steve", "rank",  "diamond");
        client.hset("players", "steve", "kills", "37");
        System.out.println("HGET steve.rank       -> " + client.hget("players", "steve", "rank").orElse("(empty)"));
        System.out.println("HGET steve.kills      -> " + client.hget("players", "steve", "kills").orElse("(empty)"));

        boolean deleted = client.delete("players", "steve:motd");
        System.out.println("DEL  steve:motd       -> deleted=" + deleted);
        System.out.println("GET  (after delete)   -> " + client.get("players", "steve:motd").orElse("(empty)"));
    }

    // -------------------------------------------------------------------------
    // 2. KeyValueStore API via RemoteNamespace — drop-in for InMemoryStore
    // -------------------------------------------------------------------------

    static void demoKeyValueStoreApi(RedisMCClient client) {
        System.out.println("\n=== KeyValueStore API (RemoteNamespace) ===");

        // Exactly the same call-site as mc.redis.core.InMemoryStore.namespace()
        KeyValueStore players = client.namespace("players");

        players.set("alex:score", 2000L);
        Optional<Object> score = players.get("alex:score");
        System.out.println("GET  alex:score       -> " + score.orElse("(empty)"));  // 2000

        long next = players.increment("alex:score", 500);
        System.out.println("INCR alex:score +500  -> " + next);                     // 2500

        players.hset("alex", "rank", "master");
        Optional<Object> rank = players.hget("alex", "rank");
        System.out.println("HGET alex.rank        -> " + rank.orElse("(empty)"));   // master

        // Because KeyValueStore throws UncheckedIOException on network failure,
        // this block needs no try/catch — same ergonomics as the in-memory store.
    }

    // -------------------------------------------------------------------------
    // 3. Async API via AsyncRedisMCClient / RemoteNamespace
    // -------------------------------------------------------------------------

    static void demoAsync(RedisMCClient client) throws InterruptedException {
        System.out.println("\n=== Async API ===");

        AsyncRedisMCClient async = client.async();
        RemoteNamespace scores  = client.namespace("scores");

        // Fire multiple writes in parallel, then join when all complete.
        List<String> playerIds = List.of("p1", "p2", "p3", "p4");
        CompletableFuture<Void> allWrites = CompletableFuture.allOf(
                playerIds.stream()
                         .map(id -> async.setAsync("scores", id, 0L))
                         .toArray(CompletableFuture[]::new)
        );
        allWrites.join();
        System.out.println("All scores initialised to 0");

        // Chain: increment p1, then immediately read the new value.
        async.incrementAsync("scores", "p1", 750)
             .thenCompose(newScore -> {
                 System.out.println("INCR p1 -> " + newScore);
                 return async.getAsync("scores", "p1");
             })
             .thenAccept(v -> System.out.println("GET  p1 -> " + v.orElse("(empty)")))
             .join();

        // Use RemoteNamespace async methods — namespace prefix handled automatically.
        scores.setAsync("p2", 300L)
              .thenCompose(v -> scores.incrementAsync("p2", 200))
              .thenAccept(v -> System.out.println("p2 after +200 -> " + v))
              .join();

        // Parallel reads — fan-out then collect.
        @SuppressWarnings("unchecked")
        CompletableFuture<Optional<String>>[] reads = playerIds.stream()
                .map(id -> scores.getAsync(id))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(reads).join();
        System.out.println("Final scores:");
        for (int i = 0; i < playerIds.size(); i++) {
            System.out.printf("  %s -> %s%n", playerIds.get(i), reads[i].join().orElse("(empty)"));
        }
    }
}
