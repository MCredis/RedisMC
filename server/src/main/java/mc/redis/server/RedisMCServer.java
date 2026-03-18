package mc.redis.server;

import mc.redis.core.InMemoryStore;
import mc.redis.core.SnapshotPersistence;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for the redisMC standalone server.
 *
 * <p>Starts a TCP server on {@value #DEFAULT_PORT}, then runs a self-contained
 * demo that connects a raw client and exercises every supported command. The
 * server continues accepting connections after the demo completes.</p>
 */
public class RedisMCServer {

    public static final int DEFAULT_PORT = 6380;

    public static void main(String[] args) throws Exception {
        InMemoryStore store = new InMemoryStore();

        // Persistence — load previous snapshot and auto-save every 5 minutes
        Path snapshotPath = Path.of("redismc.snapshot");
        SnapshotPersistence persistence = new SnapshotPersistence(
                store, snapshotPath, 5, TimeUnit.MINUTES);
        persistence.load();
        System.out.println("Snapshot persistence enabled (" + snapshotPath + ")");

        ExecutorService clientPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "redismc-client-handler");
            t.setDaemon(true);
            return t;
        });

        ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { serverSocket.close(); } catch (IOException ignored) {}
            clientPool.shutdown();
            persistence.shutdown();
            store.shutdown();
            System.out.println("redisMC stopped.");
        }, "redismc-shutdown"));

        // Accept loop runs in a daemon thread so main can continue to the demo.
        Thread acceptor = new Thread(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket conn = serverSocket.accept();
                    clientPool.submit(new ClientHandler(conn, store));
                }
            } catch (SocketException ignored) {
                // Normal: serverSocket.close() was called by the shutdown hook.
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "redismc-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();

        System.out.println("redisMC listening on port " + DEFAULT_PORT);

        // Brief pause so the accept loop is definitely ready before the demo connects.
        Thread.sleep(50);
        runDemo();

        System.out.println("\nServer running. Press Ctrl+C to stop.");
        acceptor.join(); // park main thread — server stays alive
    }

    // -------------------------------------------------------------------------
    // Self-contained demo
    // -------------------------------------------------------------------------

    /**
     * Opens one raw TCP connection and exercises all seven commands, printing
     * each request and the server's JSON response.
     */
    private static void runDemo() throws IOException {
        System.out.println("\n=== Client Demo ===");

        try (Socket socket = new Socket("localhost", DEFAULT_PORT);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            // SET — store a string value
            rpc(out, in, "{\"cmd\":\"SET\",\"namespace\":\"players\",\"key\":\"steve\",\"value\":\"hello\"}");

            // GET — retrieve it
            rpc(out, in, "{\"cmd\":\"GET\",\"namespace\":\"players\",\"key\":\"steve\"}");

            // INCR — auto-initialises missing key to 0 then adds 500
            rpc(out, in, "{\"cmd\":\"INCR\",\"namespace\":\"players\",\"key\":\"steve:score\",\"amount\":500}");

            // INCR again — accumulates
            rpc(out, in, "{\"cmd\":\"INCR\",\"namespace\":\"players\",\"key\":\"steve:score\",\"amount\":250}");

            // DECR — subtract from running total
            rpc(out, in, "{\"cmd\":\"DECR\",\"namespace\":\"players\",\"key\":\"steve:score\",\"amount\":100}");

            // HSET — write fields into a hash
            rpc(out, in, "{\"cmd\":\"HSET\",\"namespace\":\"players\",\"key\":\"steve\",\"field\":\"rank\",\"value\":\"diamond\"}");
            rpc(out, in, "{\"cmd\":\"HSET\",\"namespace\":\"players\",\"key\":\"steve\",\"field\":\"kills\",\"value\":\"37\"}");

            // HGET — read individual fields back
            rpc(out, in, "{\"cmd\":\"HGET\",\"namespace\":\"players\",\"key\":\"steve\",\"field\":\"rank\"}");
            rpc(out, in, "{\"cmd\":\"HGET\",\"namespace\":\"players\",\"key\":\"steve\",\"field\":\"kills\"}");

            // HGET on a missing field
            rpc(out, in, "{\"cmd\":\"HGET\",\"namespace\":\"players\",\"key\":\"steve\",\"field\":\"unknown\"}");

            // DELETE — remove the scalar key
            rpc(out, in, "{\"cmd\":\"DELETE\",\"namespace\":\"players\",\"key\":\"steve\"}");

            // GET after delete — returns null value
            rpc(out, in, "{\"cmd\":\"GET\",\"namespace\":\"players\",\"key\":\"steve\"}");
        }
    }

    private static void rpc(PrintWriter out, BufferedReader in, String request) throws IOException {
        System.out.println("  >> " + request);
        out.println(request);
        System.out.println("  << " + in.readLine());
    }
}
