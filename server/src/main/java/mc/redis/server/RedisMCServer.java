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
        Namespace players  = store.namespace("players");
        Namespace cooldowns = store.namespace("cooldowns");

        // Persistent keys (no TTL)
        players.set("steve:score", 4200);
        players.set("alex:score", 8100);

        players.get("steve:score").ifPresent(v -> System.out.println("steve:score     -> " + v));
        players.get("alex:score").ifPresent(v  -> System.out.println("alex:score      -> " + v));

        // TTL keys — cooldowns expire after 2 seconds
        cooldowns.set("steve:home", true, 2, TimeUnit.SECONDS);
        cooldowns.set("alex:pvp",   true, 5, TimeUnit.SECONDS);

        System.out.println("\n[t=0s] steve:home cooldown -> " + cooldowns.get("steve:home"));
        System.out.println("[t=0s] alex:pvp   cooldown -> " + cooldowns.get("alex:pvp"));

        Thread.sleep(3_000);

        // steve:home has expired; alex:pvp is still alive
        System.out.println("\n[t=3s] steve:home cooldown -> " + cooldowns.get("steve:home") + " (expected: empty)");
        System.out.println("[t=3s] alex:pvp   cooldown -> " + cooldowns.get("alex:pvp")   + " (expected: Optional[true])");

        // Persistent keys are unaffected
        System.out.println("[t=3s] steve:score         -> " + players.get("steve:score") + " (expected: Optional[4200])");

        store.shutdown();

        // TODO: parse config, bind network socket, start request loop
    }
}
