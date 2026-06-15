package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
    private static final String DEFAULT_CSV_FILE = "reports/loadtest-results-v3.csv";
    private static final String DEFAULT_ADMIN_TOKEN =
            System.getenv().getOrDefault("SECKILL_ADMIN_TOKEN", "ncku-admin");

    private static class LoadMetrics {
        int paidSuccess;
        int unpaidReserved;
        int queryOk;
        int queryMismatch;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "RESET".equalsIgnoreCase(args[0])) {
            String token = args.length > 1 ? args[1].trim() : DEFAULT_ADMIN_TOKEN;
            runResetOnly(token);
            return;
        }

        int users = parseIntArg(args, 0, 60);
        int threads = parseIntArg(args, 1, 20);
        String mode = args.length > 2 ? args[2].trim() : "RANDOM";

        boolean resetBeforeTest = false;
        boolean exportCsv = false;
        String csvPath = DEFAULT_CSV_FILE;
        String adminToken = DEFAULT_ADMIN_TOKEN;
        int payRate = 100;
        for (int i = 3; i < args.length; i++) {
            String flag = args[i] == null ? "" : args[i].trim();
            if ("RESET".equalsIgnoreCase(flag)) {
                resetBeforeTest = true;
                continue;
            }
            if ("CSV".equalsIgnoreCase(flag)) {
                exportCsv = true;
                continue;
            }
            if (flag.regionMatches(true, 0, "CSV=", 0, 4)) {
                exportCsv = true;
                String value = flag.substring(4).trim();
                if (!value.isEmpty()) {
                    csvPath = value;
                }
                continue;
            }
            if (flag.regionMatches(true, 0, "TOKEN=", 0, 6)) {
                String value = flag.substring(6).trim();
                if (!value.isEmpty()) {
                    adminToken = value;
                }
                continue;
            }
            if (flag.regionMatches(true, 0, "PAYRATE=", 0, 8)) {
                String value = flag.substring(8).trim();
                if (!value.isEmpty()) {
                    try {
                        payRate = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        // 使用預設值
                    }
                }
            }
        }
        if (payRate < 0) {
            payRate = 0;
        }
        if (payRate > 100) {
            payRate = 100;
        }
        final int payRateFinal = payRate;

        System.out.println("=== Seckill Load Test Start ===");
        System.out.println("Server: " + SERVER_IP + ":" + SERVER_PORT);
        System.out.println("Users: " + users + ", Threads: " + threads + ", Mode: " + mode);
        System.out.println("PayRate: " + payRateFinal + "%");

        if (resetBeforeTest) {
            String resetResp = sendRequest("RESET|" + adminToken);
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
        LoadMetrics metrics = new LoadMetrics();

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
                        String orderId = parseOrderId(response);
                        if (orderId != null) {
                            if ((idx % 100) < payRateFinal) {
                                String payResp = sendRequest("PAY|" + orderId);
                                if (payResp != null && payResp.startsWith("SUCCESS:")) {
                                    synchronized (metrics) {
                                        metrics.paidSuccess++;
                                    }
                                }
                            } else {
                                synchronized (metrics) {
                                    metrics.unpaidReserved++;
                                }
                            }

                            String queryResp = sendRequest("QUERY|" + phone);
                            if (queryResp != null && queryResp.startsWith("QUERY_RES:")) {
                                synchronized (metrics) {
                                    metrics.queryOk++;
                                }
                            } else {
                                synchronized (metrics) {
                                    metrics.queryMismatch++;
                                }
                            }
                        }
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
        System.out.println("Paid success: " + metrics.paidSuccess + ", Unpaid reserved: " + metrics.unpaidReserved);
        System.out.println("Query OK: " + metrics.queryOk + ", Query mismatch: " + metrics.queryMismatch);

        if (!failReason.isEmpty()) {
            System.out.println("\nFailed reason breakdown:");
            Map<String, AtomicInteger> sorted = new TreeMap<>(failReason);
            for (Map.Entry<String, AtomicInteger> e : sorted.entrySet()) {
                System.out.println("- " + e.getKey() + " => " + e.getValue().get());
            }
        }

        if (exportCsv) {
            String csvResult = appendCsvRecord(csvPath, users, threads, mode, resetBeforeTest,
                    successCount.get(), failedCount.get(), avgLatency, p95Latency, runMs,
                    metrics.paidSuccess, metrics.unpaidReserved, metrics.queryOk, metrics.queryMismatch,
                    payRateFinal, failReason);
            System.out.println("\nCSV export: " + csvResult);
        }

        String afterStatus = sendRequest("STATUS");
        System.out.println("\nStatus(after): " + afterStatus);
        System.out.println("=== Seckill Load Test End ===");
    }

    private static String appendCsvRecord(
            String csvPath,
            int users,
            int threads,
            String mode,
            boolean resetBeforeTest,
            int success,
            int failed,
            long avgLatency,
            long p95Latency,
            long runMs,
                int paidSuccess,
                int unpaidReserved,
                int queryOk,
                int queryMismatch,
                int payRate,
            Map<String, AtomicInteger> failReason
    ) {
        try {
            final String header = "timestamp,users,threads,mode,reset_before_test,pay_rate,success,failed,paid_success,unpaid_reserved,query_ok,query_mismatch,avg_latency_ms,p95_latency_ms,run_ms,top_fail_reason,top_fail_count";
            Path path = Paths.get(csvPath);
            if (Files.exists(path) && Files.size(path) > 0) {
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String firstLine = reader.readLine();
                    if (firstLine != null && !header.equals(firstLine)) {
                        String fileName = path.getFileName().toString();
                        int dot = fileName.lastIndexOf('.');
                        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
                        String ext = dot > 0 ? fileName.substring(dot) : "";
                        path = path.resolveSibling(baseName + "-v3" + ext);
                    }
                }
            }
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            boolean writeHeader = !Files.exists(path) || Files.size(path) == 0;
            String timestamp = String.valueOf(System.currentTimeMillis());
            String topFailReason = "NONE";
            int topFailCount = 0;
            for (Map.Entry<String, AtomicInteger> entry : failReason.entrySet()) {
                int count = entry.getValue().get();
                if (count > topFailCount) {
                    topFailCount = count;
                    topFailReason = entry.getKey();
                }
            }

            try (BufferedWriter writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                if (writeHeader) {
                    writer.write(header);
                    writer.newLine();
                }

                writer.write(csvEscape(timestamp));
                writer.write(',');
                writer.write(String.valueOf(users));
                writer.write(',');
                writer.write(String.valueOf(threads));
                writer.write(',');
                writer.write(csvEscape(mode));
                writer.write(',');
                writer.write(String.valueOf(resetBeforeTest));
                writer.write(',');
                writer.write(String.valueOf(payRate));
                writer.write(',');
                writer.write(String.valueOf(success));
                writer.write(',');
                writer.write(String.valueOf(failed));
                writer.write(',');
                writer.write(String.valueOf(paidSuccess));
                writer.write(',');
                writer.write(String.valueOf(unpaidReserved));
                writer.write(',');
                writer.write(String.valueOf(queryOk));
                writer.write(',');
                writer.write(String.valueOf(queryMismatch));
                writer.write(',');
                writer.write(String.valueOf(avgLatency));
                writer.write(',');
                writer.write(String.valueOf(p95Latency));
                writer.write(',');
                writer.write(String.valueOf(runMs));
                writer.write(',');
                writer.write(csvEscape(topFailReason));
                writer.write(',');
                writer.write(String.valueOf(topFailCount));
                writer.newLine();
            }

            return "OK -> " + path.toAbsolutePath();
        } catch (Exception ex) {
            return "FAILED -> " + ex.getMessage();
        }
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static void runResetOnly(String token) {
        System.out.println("=== Seckill Reset Command ===");
        System.out.println("Server: " + SERVER_IP + ":" + SERVER_PORT);

        String beforeStatus = sendRequest("STATUS");
        System.out.println("Status(before): " + beforeStatus);

        String resetResponse = sendRequest("RESET|" + token);
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

    private static String parseOrderId(String response) {
        if (response == null || !response.startsWith("SUCCESS:")) {
            return null;
        }

        int marker = response.indexOf("訂單#");
        if (marker < 0) {
            return null;
        }
        int start = marker + 3;
        int end = start;
        while (end < response.length() && Character.isDigit(response.charAt(end))) {
            end++;
        }
        if (end <= start) {
            return null;
        }
        return response.substring(start, end);
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
