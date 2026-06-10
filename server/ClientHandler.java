package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private static final String ADMIN_TOKEN =
            System.getenv().getOrDefault("SECKILL_ADMIN_TOKEN", "ncku-admin");

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String request = in.readLine();
            if (request == null || request.trim().isEmpty()) {
                out.println("FAILED:請求內容不可為空");
                return;
            }

            if ("STATUS".equalsIgnoreCase(request.trim())) {
                out.println(TicketManager.getTicketStatus());
                return;
            }

            if (request.trim().startsWith("RESET")) {
                String[] parts = request.split("\\|", -1);
                if (parts.length != 2) {
                    out.println("FAILED:RESET 格式錯誤，請使用 RESET|token");
                    return;
                }

                if (!ADMIN_TOKEN.equals(parts[1].trim())) {
                    out.println("FAILED:未授權的 RESET 指令");
                    return;
                }

                out.println(TicketManager.resetState());
                return;
            }

            if (request.trim().startsWith("ADMIN|")) {
                String[] parts = request.split("\\|", -1);
                if (parts.length != 3) {
                    out.println("FAILED:ADMIN 格式錯誤，請使用 ADMIN|SUMMARY|token 或 ADMIN|ORDERS|token");
                    return;
                }

                if (!ADMIN_TOKEN.equals(parts[2].trim())) {
                    out.println("FAILED:未授權的 ADMIN 指令");
                    return;
                }

                if ("SUMMARY".equalsIgnoreCase(parts[1].trim())) {
                    out.println(TicketManager.getAdminSummary());
                    return;
                }

                if ("ORDERS".equalsIgnoreCase(parts[1].trim())) {
                    out.println(TicketManager.getAdminOrders());
                    return;
                }

                out.println("FAILED:未知的 ADMIN 子指令，僅支援 SUMMARY 或 ORDERS");
                return;
            }

            if (request.startsWith("BOOK|")) {
                // 格式：BOOK|票種|姓名|電話|張數
                String[] parts = request.split("\\|", -1);
                if (parts.length != 5) {
                    out.println("FAILED:BOOK 請求格式錯誤");
                    return;
                }

                String ticketType = parts[1].trim();
                String userName = parts[2].trim();
                String phone = parts[3].trim();
                int quantity;
                try {
                    quantity = Integer.parseInt(parts[4].trim());
                } catch (NumberFormatException ex) {
                    out.println("FAILED:張數必須是數字");
                    return;
                }

                out.println(TicketManager.tryToBook(userName, phone, ticketType, quantity));
                return;
            }

            out.println("FAILED:不支援的請求，請使用 STATUS / BOOK / RESET|token / ADMIN|SUMMARY|token / ADMIN|ORDERS|token");
        } catch (Exception e) {
            System.err.println("處理客戶端請求時噴錯: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }
}
