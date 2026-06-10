package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SeckillAdminTool {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8888;

    public static void main(String[] args) {
        String action = args.length > 0 ? args[0].trim().toUpperCase() : "SUMMARY";
        String request;

        switch (action) {
            case "SUMMARY":
                request = "ADMIN|SUMMARY";
                break;
            case "ORDERS":
                request = "ADMIN|ORDERS";
                break;
            default:
                System.out.println("Usage: java client.SeckillAdminTool [SUMMARY|ORDERS]");
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
            return in.readLine();
        } catch (Exception ex) {
            return null;
        }
    }
}
