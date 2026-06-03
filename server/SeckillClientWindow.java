package server;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SeckillClientWindow extends JFrame {
    private static final String SERVER_IP = "127.0.0.1"; // 課堂Demo時改成Server電腦的局域網IP (如 192.168.x.x)
    private static final int SERVER_PORT = 8888;

    private JTextField txtUserId;
    private JButton btnGrab;
    private JLabel lblStatus;

    public SeckillClientWindow() {
        // 初始化 Swing 視窗設定
        setTitle("NCKU 資工期末搶票模擬器");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 視窗置中
        setLayout(new GridLayout(3, 1, 10, 10));

        // 第一行：輸入欄位
        JPanel panelInput = new JPanel(new FlowLayout());
        panelInput.add(new JLabel("學號 / 學號暱稱:"));
        txtUserId = new JTextField(15);
        panelInput.add(txtUserId);
        add(panelInput);

        // 第二行：搶票按鈕
        JPanel panelBtn = new JPanel(new FlowLayout());
        btnGrab = new JButton("🔥 立即搶票 🔥");
        btnGrab.setFont(new Font("微軟正黑體", Font.BOLD, 16));
        panelBtn.add(btnGrab);
        add(panelBtn);

        // 第三行：狀態顯示
        lblStatus = new JLabel("請輸入名稱後點擊按鈕開始搶票", SwingConstants.CENTER);
        lblStatus.setForeground(Color.BLUE);
        add(lblStatus);

        // 點擊按鈕後的事件監聽 (Network 觸發)
        btnGrab.addActionListener(e -> performGrabAction());
    }

    private void performGrabAction() {
        String userId = txtUserId.getText().trim();
        if (userId.isEmpty()) {
            lblStatus.setText("❌ 請先輸入學號或名稱！");
            lblStatus.setForeground(Color.RED);
            return;
        }

        lblStatus.setText("正在與中央伺服器連線中...");
        
        // 核心網路通訊：點擊後才開啟 Socket 連線 (短連線設計)
        try (
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // 1. 送出搶票人 ID
            out.println(userId);
            
            // 2. 接收伺服器回傳結果
            String response = in.readLine();
            
            // 3. 根據結果更新 Swing 畫面
            if (response.startsWith("SUCCESS")) {
                lblStatus.setText("🎉 " + response.split(":")[1]);
                lblStatus.setForeground(new Color(0, 128, 0)); // 綠色
                JOptionPane.showMessageDialog(this, response.split(":")[1], "搶票成功", JOptionPane.INFORMATION_MESSAGE);
            } else {
                lblStatus.setText("😭 " + response.split(":")[1]);
                lblStatus.setForeground(Color.RED);
            }
            
        } catch (Exception ex) {
            lblStatus.setText("❌ 無法連線到伺服器: " + ex.getMessage());
            lblStatus.setForeground(Color.RED);
        }
    }

    public static void main(String[] args) {
        // 確保 Swing 在事件發送執行緒（Event Dispatch Thread）中執行，符合 Thread 規範
        SwingUtilities.invokeLater(() -> {
            new SeckillClientWindow().setVisible(true);
        });
    }
}