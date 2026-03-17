package mc.redis.client;

/**
 * Demonstrates basic usage of the RedisMCClient API.
 */
public class RedisMCClientExample {

    public static void main(String[] args) {
        RedisMCClient client = new RedisMCClient("localhost", 6380);

        // Store a player's score in the "scores" namespace
        client.set("scores", "player:steve", "4200");

        // Store with a 60-second TTL (e.g. a cooldown)
        client.set("cooldowns", "player:steve:home", "true", 60_000);

        // Retrieve a value
        client.get("scores", "player:steve")
                .ifPresent(score -> System.out.println("Steve's score: " + score));

        // Check existence
        boolean onCooldown = client.exists("cooldowns", "player:steve:home");
        System.out.println("Home cooldown active: " + onCooldown);

        // Delete a key
        client.delete("scores", "player:steve");

        client.close();
    }
}
