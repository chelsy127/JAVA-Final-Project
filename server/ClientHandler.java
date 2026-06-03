package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
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
            // 讀取客戶端傳過來的使用者名稱
            String userId = in.readLine();
            if (userId != null && !userId.trim().isEmpty()) {
                // 呼叫中央票務系統進行搶票
                String result = TicketManager.tryToBook(userId);
                // 將結果回傳給客戶端
                out.println(result);
            }
        } catch (Exception e) {
            System.err.println("處理客戶端請求時噴錯: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }
}
