package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SeckillAdminTool {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8888;
    private static final String DEFAULT_ADMIN_TOKEN =
            System.getenv().getOrDefault("SECKILL_ADMIN_TOKEN", "ncku-admin");

    public static void main(String[] args) {
        String action = args.length > 0 ? args[0].trim().toUpperCase() : "SUMMARY";
        String request;

        switch (action) {
            case "SUMMARY":
                String summaryToken = args.length > 1 ? args[1].trim() : DEFAULT_ADMIN_TOKEN;
                request = "ADMIN|SUMMARY|" + summaryToken;
                break;
            case "ORDERS":
                String ordersToken = args.length > 1 ? args[1].trim() : DEFAULT_ADMIN_TOKEN;
                request = "ADMIN|ORDERS|" + ordersToken;
                break;
            case "PAY":
                if (args.length < 2 || args[1].trim().isEmpty()) {
                    System.out.println("Usage: java client.SeckillAdminTool PAY <orderId>");
                    return;
                }
                request = "PAY|" + args[1].trim();
                break;
            case "QUERY":
                if (args.length < 2 || args[1].trim().isEmpty()) {
                    System.out.println("Usage: java client.SeckillAdminTool QUERY <phone>");
                    return;
                }
                request = "QUERY|" + args[1].trim();
                break;
            default:
                System.out.println("Usage:");
                System.out.println("  java client.SeckillAdminTool SUMMARY [token]");
                System.out.println("  java client.SeckillAdminTool ORDERS [token]");
                System.out.println("  java client.SeckillAdminTool PAY <orderId>");
                System.out.println("  java client.SeckillAdminTool QUERY <phone>");
                return;
        }

        String response = sendRequest(request);
        if (response == null) {
            System.out.println("FAILED:無法連線伺服器");
            return;
        }

        System.out.println(response);
    }

    private static String sendRequest(String request) {
        try (
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            out.println(request);
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (response.length() > 0) {
                    response.append(System.lineSeparator());
                }
                response.append(line);
            }
            return response.length() == 0 ? null : response.toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
