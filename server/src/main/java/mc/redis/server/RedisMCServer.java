package mc.redis.server;

import mc.redis.core.InMemoryStore;
import mc.redis.core.Namespace;

/**
 * Entry point for the redisMC standalone server process.
 */
public class RedisMCServer {

    public static void main(String[] args) {
        System.out.println("redisMC server starting...");

        InMemoryStore store = new InMemoryStore();

        Namespace players = store.namespace("players");
        Namespace server  = store.namespace("server");

        // set
        players.set("steve:score", 4200);
        players.set("alex:score", 8100);
        server.set("motd", "Welcome to redisMC!");
        server.set("maintenance", false);

        // get — each namespace only sees its own keys
        players.get("steve:score").ifPresent(v -> System.out.println("players:steve:score -> " + v));
        players.get("alex:score").ifPresent(v  -> System.out.println("players:alex:score  -> " + v));
        server.get("motd").ifPresent(v         -> System.out.println("server:motd         -> " + v));
        server.get("maintenance").ifPresent(v  -> System.out.println("server:maintenance  -> " + v));

        // namespaces are isolated — "motd" under players returns nothing
        System.out.println("players:motd (should be empty) -> " + players.get("motd"));

        // delete
        boolean deleted = players.delete("steve:score");
        System.out.println("deleted steve:score: " + deleted);
        System.out.println("players:steve:score after delete -> " + players.get("steve:score"));

        // TODO: parse config, bind network socket, start request loop
    }
}
