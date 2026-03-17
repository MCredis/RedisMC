package mc.redis.server;

import mc.redis.core.InMemoryStore;

/**
 * Entry point for the redisMC standalone server process.
 */
public class RedisMCServer {

    public static void main(String[] args) {
        System.out.println("redisMC server starting...");

        InMemoryStore store = new InMemoryStore();

        // set
        store.set("player:steve:score", 4200);
        store.set("server:motd", "Welcome to redisMC!");
        store.set("flag:maintenance", true);

        // get
        store.get("player:steve:score").ifPresent(v -> System.out.println("score   -> " + v));
        store.get("server:motd").ifPresent(v    -> System.out.println("motd    -> " + v));
        store.get("flag:maintenance").ifPresent(v -> System.out.println("maint   -> " + v));
        System.out.println("missing -> " + store.get("does:not:exist"));

        // delete
        boolean deleted = store.delete("player:steve:score");
        System.out.println("deleted score: " + deleted);
        System.out.println("score after delete -> " + store.get("player:steve:score"));

        // TODO: parse config, bind network socket, start request loop
    }
}
