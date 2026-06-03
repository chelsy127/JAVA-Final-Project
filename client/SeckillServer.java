package client;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import server.ClientHandler;

public class SeckillServer {
    private static final int PORT = 8888;
    // 使用執行緒池來管理並發的客戶端連線
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(20);

    public static void main(String[] args) {
        System.out.println("=== 搶票中央伺服器已啟動，監聽 Port: " + PORT + " ===");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // 阻塞等待組員的電腦（Client）連線進來
                Socket clientSocket = serverSocket.accept();
                System.out.println("偵測到新客戶端連線: " + clientSocket.getRemoteSocketAddress());
                
                // 丟給執行緒池非同步處理，才不會阻塞下一個人的連線
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("伺服器異常: " + e.getMessage());
        }
    }
}
