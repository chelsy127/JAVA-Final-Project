package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SeckillLoadTest {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8888;
    private static final String[] TICKET_TYPES = {"VIP", "A區", "B區"};

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "RESET".equalsIgnoreCase(args[0])) {
            runResetOnly();
            return;
        }

        int users = parseIntArg(args, 0, 60);
        int threads = parseIntArg(args, 1, 20);
        String mode = args.length > 2 ? args[2].trim() : "RANDOM";
        boolean resetBeforeTest = args.length > 3 && "RESET".equalsIgnoreCase(args[3]);

        System.out.println("=== Seckill Load Test Start ===");
        System.out.println("Server: " + SERVER_IP + ":" + SERVER_PORT);
        System.out.println("Users: " + users + ", Threads: " + threads + ", Mode: " + mode);

        if (resetBeforeTest) {
            String resetResp = sendRequest("RESET");
            System.out.println("Reset(before): " + resetResp);
        }

        String beforeStatus = sendRequest("STATUS");
        System.out.println("Status(before): " + beforeStatus);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(users);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        Map<String, AtomicInteger> failReason = new ConcurrentHashMap<>();
        List<Long> latencies = new ArrayList<>();

        long begin = System.currentTimeMillis();
        for (int i = 0; i < users; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    String ticketType = resolveTicketType(mode, idx);
                    int quantity = 1 + (idx % 4);
                    String name = "user" + idx;
                    String phone = "09" + String.format("%08d", idx);
                    String request = "BOOK|" + ticketType + "|" + name + "|" + phone + "|" + quantity;

                    long t1 = System.nanoTime();
                    String response = sendRequest(request);
                    long t2 = System.nanoTime();

                    synchronized (latencies) {
                        latencies.add(TimeUnit.NANOSECONDS.toMillis(t2 - t1));
                    }

                    if (response != null && response.startsWith("SUCCESS:")) {
                        successCount.incrementAndGet();
                    } else {
                        failedCount.incrementAndGet();
                        String key = normalizeFailReason(response);
                        failReason.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
                    }
                } catch (Exception ex) {
                    failedCount.incrementAndGet();
                    failReason.computeIfAbsent("EXCEPTION", k -> new AtomicInteger(0)).incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        done.await();
        long end = System.currentTimeMillis();

        pool.shutdown();

        long warmupMs = startTime - begin;
        long runMs = end - startTime;
        long totalMs = end - begin;

        long avgLatency = avg(latencies);
        long p95Latency = p95(latencies);

        System.out.println("\n=== Result Summary ===");
        System.out.println("Warmup(ms): " + warmupMs + ", Run(ms): " + runMs + ", Total(ms): " + totalMs);
        System.out.println("Success: " + successCount.get() + ", Failed: " + failedCount.get());
        System.out.println("Avg latency(ms): " + avgLatency + ", P95 latency(ms): " + p95Latency);

        if (!failReason.isEmpty()) {
            System.out.println("\nFailed reason breakdown:");
            Map<String, AtomicInteger> sorted = new TreeMap<>(failReason);
            for (Map.Entry<String, AtomicInteger> e : sorted.entrySet()) {
                System.out.println("- " + e.getKey() + " => " + e.getValue().get());
            }
        }

        String afterStatus = sendRequest("STATUS");
        System.out.println("\nStatus(after): " + afterStatus);
        System.out.println("=== Seckill Load Test End ===");
    }

    private static void runResetOnly() {
        System.out.println("=== Seckill Reset Command ===");
        System.out.println("Server: " + SERVER_IP + ":" + SERVER_PORT);

        String beforeStatus = sendRequest("STATUS");
        System.out.println("Status(before): " + beforeStatus);

        String resetResponse = sendRequest("RESET");
        System.out.println("Reset(response): " + resetResponse);

        String afterStatus = sendRequest("STATUS");
        System.out.println("Status(after): " + afterStatus);
        System.out.println("=== Reset End ===");
    }

    private static int parseIntArg(String[] args, int index, int defaultValue) {
        if (args.length <= index) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static String resolveTicketType(String mode, int idx) {
        if ("RANDOM".equalsIgnoreCase(mode)) {
            return TICKET_TYPES[idx % TICKET_TYPES.length];
        }
        for (String type : TICKET_TYPES) {
            if (type.equalsIgnoreCase(mode)) {
                return type;
            }
        }
        return TICKET_TYPES[idx % TICKET_TYPES.length];
    }

    private static String normalizeFailReason(String response) {
        if (response == null) {
            return "NO_RESPONSE";
        }
        if (response.startsWith("FAILED:")) {
            return response.substring("FAILED:".length()).trim();
        }
        return "UNKNOWN_RESPONSE";
    }

    private static long avg(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private static long p95(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        values.sort(Long::compareTo);
        int index = (int) Math.ceil(values.size() * 0.95) - 1;
        if (index < 0) {
            index = 0;
        }
        return values.get(index);
    }

    private static String sendRequest(String request) {
        try (
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            out.println(request);
            return in.readLine();
        } catch (Exception ex) {
            return null;
        }
    }
}
