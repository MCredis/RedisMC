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
        Namespace players = store.namespace("players");
        Namespace sessions = store.namespace("sessions");

        // --- basic hset / hget -----------------------------------------------
        players.hset("steve", "score", 4200);
        players.hset("steve", "rank",  "diamond");
        players.hset("steve", "kills", 37);

        players.hget("steve", "score").ifPresent(v -> System.out.println("steve.score  -> " + v)); // 4200
        players.hget("steve", "rank").ifPresent(v  -> System.out.println("steve.rank   -> " + v)); // diamond
        players.hget("steve", "kills").ifPresent(v -> System.out.println("steve.kills  -> " + v)); // 37

        // missing field returns empty
        System.out.println("steve.unknown -> " + players.hget("steve", "unknown")); // Optional.empty

        // --- multiple hash keys in same namespace ----------------------------
        players.hset("alex", "score", 8100);
        players.hset("alex", "rank",  "master");

        players.hget("alex", "score").ifPresent(v -> System.out.println("alex.score   -> " + v)); // 8100

        // --- namespace isolation ---------------------------------------------
        // "steve" under sessions is a completely separate hash key
        sessions.hset("steve", "ip", "192.168.1.10");
        sessions.hset("steve", "joined", "2026-03-17");

        sessions.hget("steve", "ip").ifPresent(v -> System.out.println("session.ip   -> " + v));     // 192.168.1.10
        System.out.println("player.ip (should be empty) -> " + players.hget("steve", "ip")); // Optional.empty

        // --- hset field update preserves other fields and TTL ----------------
        players.hset("steve", "score", 5000); // overwrite one field
        players.hget("steve", "score").ifPresent(v -> System.out.println("steve.score updated -> " + v)); // 5000
        players.hget("steve", "rank").ifPresent(v  -> System.out.println("steve.rank intact   -> " + v)); // diamond

        // --- TTL on a hash key -----------------------------------------------
        // First hset creates the hash and sets a 2-second TTL on the whole key.
        sessions.hset("alex", "ip", "10.0.0.5", 2, TimeUnit.SECONDS);
        sessions.hset("alex", "joined", "2026-03-17"); // subsequent hset preserves TTL

        System.out.println("\n[t=0s] session alex.ip     -> " + sessions.hget("alex", "ip"));     // Optional[10.0.0.5]
        System.out.println("[t=0s] session alex.joined -> " + sessions.hget("alex", "joined"));  // Optional[2026-03-17]

        Thread.sleep(3_000);

        // Both fields gone — TTL expired the entire hash key
        System.out.println("\n[t=3s] session alex.ip     -> " + sessions.hget("alex", "ip"));     // Optional.empty
        System.out.println("[t=3s] session alex.joined -> " + sessions.hget("alex", "joined"));  // Optional.empty

        // player hashes are persistent — unaffected
        System.out.println("[t=3s] player steve.score  -> " + players.hget("steve", "score"));   // Optional[5000]

        store.shutdown();

        // TODO: parse config, bind network socket, start request loop
    }
}
