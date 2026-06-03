package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class SeckillClientWindow extends JFrame {
    private static final String SERVER_IP = "127.0.0.1"; // 課堂Demo時改成Server電腦的局域網IP (如 192.168.x.x)
    private static final int SERVER_PORT = 8888;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final Map<String, TicketSnapshot> ticketSnapshots = new LinkedHashMap<>();

    private JComboBox<String> cmbTicketType;
    private JLabel lblTicketHint;
    private JLabel lblFormTicketInfo;

    private JTextField txtName;
    private JTextField txtPhone;
    private JTextField txtQuantity;
    private JTextField txtCaptchaInput;
    private JLabel lblCaptcha;

    private JLabel lblStatus;
    private String currentCaptcha = "";

    private static class TicketSnapshot {
        private final int remaining;
        private final int price;

        private TicketSnapshot(int remaining, int price) {
            this.remaining = remaining;
            this.price = price;
        }
    }

    public SeckillClientWindow() {
        setTitle("NCKU 資工期末搶票模擬器 - 改版");
        setSize(620, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        refreshTicketStatus();
        refreshCaptcha();
    }

    private JComponent buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(12, 16, 8, 16));
        panel.setBackground(new Color(18, 43, 78));

        JLabel title = new JLabel("NCKU Event Ticket Seckill Center");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("微軟正黑體", Font.BOLD, 22));
        panel.add(title, BorderLayout.WEST);

        JLabel subtitle = new JLabel("兩步驟下單：選票種 -> 填資料 + 驗證碼");
        subtitle.setForeground(new Color(199, 230, 255));
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildCenterPanel() {
        cardPanel.add(buildSelectPanel(), "SELECT");
        cardPanel.add(buildFormPanel(), "FORM");
        cardLayout.show(cardPanel, "SELECT");
        return cardPanel;
    }

    private JComponent buildStatusBar() {
        lblStatus = new JLabel("系統就緒，請先選擇票種", SwingConstants.CENTER);
        lblStatus.setOpaque(true);
        lblStatus.setBackground(new Color(245, 247, 250));
        lblStatus.setBorder(new EmptyBorder(8, 8, 8, 8));
        lblStatus.setForeground(new Color(30, 73, 140));
        return lblStatus;
    }

    private JPanel buildSelectPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(252, 252, 252));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel lblTitle = new JLabel("Step 1. 選擇你要搶的票種");
        lblTitle.setFont(new Font("微軟正黑體", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(lblTitle, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        panel.add(new JLabel("票種："), gbc);

        cmbTicketType = new JComboBox<>();
        cmbTicketType.setPreferredSize(new Dimension(220, 30));
        cmbTicketType.addActionListener(e -> updateTicketHint());
        gbc.gridx = 1;
        panel.add(cmbTicketType, gbc);

        lblTicketHint = new JLabel("請先載入票況");
        lblTicketHint.setFont(new Font("微軟正黑體", Font.PLAIN, 15));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(lblTicketHint, gbc);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        buttonBar.setOpaque(false);
        JButton btnRefresh = new JButton("重新整理票況");
        JButton btnNext = new JButton("下一步：填寫購票資料");
        btnNext.setBackground(new Color(17, 94, 190));
        btnNext.setForeground(Color.WHITE);

        btnRefresh.addActionListener(e -> refreshTicketStatus());
        btnNext.addActionListener(e -> gotoFormStep());

        buttonBar.add(btnRefresh);
        buttonBar.add(btnNext);
        gbc.gridy = 3;
        panel.add(buttonBar, gbc);
        return panel;
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(250, 253, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel lblTitle = new JLabel("Step 2. 填寫購票資訊");
        lblTitle.setFont(new Font("微軟正黑體", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(lblTitle, gbc);

        lblFormTicketInfo = new JLabel("票種資訊：");
        gbc.gridy = 1;
        panel.add(lblFormTicketInfo, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 2;
        panel.add(new JLabel("姓名："), gbc);
        txtName = new JTextField(18);
        gbc.gridx = 1;
        panel.add(txtName, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("電話："), gbc);
        txtPhone = new JTextField(18);
        gbc.gridx = 1;
        panel.add(txtPhone, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(new JLabel("張數 (1~4)："), gbc);
        txtQuantity = new JTextField("1", 18);
        gbc.gridx = 1;
        panel.add(txtQuantity, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        panel.add(new JLabel("驗證碼："), gbc);
        JPanel captchaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        captchaPanel.setOpaque(false);
        lblCaptcha = new JLabel("----");
        lblCaptcha.setFont(new Font("Consolas", Font.BOLD, 20));
        txtCaptchaInput = new JTextField(8);
        JButton btnRefreshCaptcha = new JButton("換一張");
        btnRefreshCaptcha.addActionListener(e -> refreshCaptcha());
        captchaPanel.add(lblCaptcha);
        captchaPanel.add(txtCaptchaInput);
        captchaPanel.add(btnRefreshCaptcha);
        gbc.gridx = 1;
        panel.add(captchaPanel, gbc);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        buttonBar.setOpaque(false);
        JButton btnBack = new JButton("返回票種選擇");
        JButton btnSubmit = new JButton("確認送出搶票");
        btnSubmit.setBackground(new Color(0, 128, 85));
        btnSubmit.setForeground(Color.WHITE);
        btnBack.addActionListener(e -> cardLayout.show(cardPanel, "SELECT"));
        btnSubmit.addActionListener(e -> submitBooking());
        buttonBar.add(btnBack);
        buttonBar.add(btnSubmit);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        panel.add(buttonBar, gbc);
        return panel;
    }

    private void refreshTicketStatus() {
        String response = sendRequest("STATUS");
        if (response == null) {
            setStatus("❌ 目前無法取得票況", Color.RED);
            return;
        }

        if (!response.startsWith("STATUS:")) {
            setStatus("❌ 票況格式錯誤：" + response, Color.RED);
            return;
        }

        ticketSnapshots.clear();
        String payload = response.substring("STATUS:".length());
        if (!payload.trim().isEmpty()) {
            String[] items = payload.split(",");
            for (String item : items) {
                String[] typeAndValue = item.split("=");
                if (typeAndValue.length != 2) {
                    continue;
                }
                String type = typeAndValue[0].trim();
                String[] remainAndPrice = typeAndValue[1].split("@");
                if (remainAndPrice.length != 2) {
                    continue;
                }

                try {
                    int remaining = Integer.parseInt(remainAndPrice[0].trim());
                    int price = Integer.parseInt(remainAndPrice[1].trim());
                    ticketSnapshots.put(type, new TicketSnapshot(remaining, price));
                } catch (NumberFormatException ignored) {
                    // 略過格式錯誤的票種資料
                }
            }
        }

        String previous = (String) cmbTicketType.getSelectedItem();
        cmbTicketType.removeAllItems();
        for (String type : ticketSnapshots.keySet()) {
            cmbTicketType.addItem(type);
        }
        if (previous != null && ticketSnapshots.containsKey(previous)) {
            cmbTicketType.setSelectedItem(previous);
        }

        updateTicketHint();
        setStatus("✅ 已更新最新票況", new Color(0, 110, 50));
    }

    private void gotoFormStep() {
        String selectedType = (String) cmbTicketType.getSelectedItem();
        if (selectedType == null) {
            setStatus("❌ 尚無可用票種", Color.RED);
            return;
        }

        TicketSnapshot snapshot = ticketSnapshots.get(selectedType);
        if (snapshot == null) {
            setStatus("❌ 找不到票種資訊，請重新整理", Color.RED);
            return;
        }

        lblFormTicketInfo.setText("票種：" + selectedType + " | 單價：$" + snapshot.price + " | 剩餘：" + snapshot.remaining + " 張");
        refreshCaptcha();
        cardLayout.show(cardPanel, "FORM");
        setStatus("請填寫姓名、電話、張數後送出", new Color(30, 73, 140));
    }

    private void submitBooking() {
        String ticketType = (String) cmbTicketType.getSelectedItem();
        if (ticketType == null) {
            setStatus("❌ 請先選擇票種", Color.RED);
            return;
        }

        String name = txtName.getText().trim();
        String phone = txtPhone.getText().trim();
        String qtyText = txtQuantity.getText().trim();
        String captchaInput = txtCaptchaInput.getText().trim();

        if (name.isEmpty() || phone.isEmpty() || qtyText.isEmpty()) {
            setStatus("❌ 請完整填寫姓名、電話與張數", Color.RED);
            return;
        }

        if (name.contains("|") || phone.contains("|")) {
            setStatus("❌ 姓名與電話不可包含 | 字元", Color.RED);
            return;
        }

        if (!captchaInput.equalsIgnoreCase(currentCaptcha)) {
            setStatus("❌ 驗證碼錯誤，請再試一次", Color.RED);
            refreshCaptcha();
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(qtyText);
        } catch (NumberFormatException ex) {
            setStatus("❌ 張數必須是數字", Color.RED);
            return;
        }

        if (quantity <= 0 || quantity > 4) {
            setStatus("❌ 張數需介於 1 到 4", Color.RED);
            return;
        }

        String response = sendRequest("BOOK|" + ticketType + "|" + name + "|" + phone + "|" + quantity);
        if (response == null) {
            setStatus("❌ 下單失敗，無法連線伺服器", Color.RED);
            return;
        }

        if (response.startsWith("SUCCESS:")) {
            String message = response.substring("SUCCESS:".length());
            JOptionPane.showMessageDialog(this, message, "購票成功", JOptionPane.INFORMATION_MESSAGE);
            setStatus("🎉 " + message, new Color(0, 128, 0));
            txtCaptchaInput.setText("");
            refreshTicketStatus();
            cardLayout.show(cardPanel, "SELECT");
            return;
        }

        String failMessage = response.startsWith("FAILED:")
                ? response.substring("FAILED:".length())
                : response;
        setStatus("😭 " + failMessage, Color.RED);
        refreshTicketStatus();
        refreshCaptcha();
    }

    private void updateTicketHint() {
        String selectedType = (String) cmbTicketType.getSelectedItem();
        if (selectedType == null) {
            lblTicketHint.setText("目前沒有可顯示票種");
            return;
        }

        TicketSnapshot snapshot = ticketSnapshots.get(selectedType);
        if (snapshot == null) {
            lblTicketHint.setText("請重新整理票況");
            return;
        }

        lblTicketHint.setText("票種「" + selectedType + "」：剩餘 " + snapshot.remaining + " 張，單價 $" + snapshot.price);
    }

    private void refreshCaptcha() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        currentCaptcha = builder.toString();
        lblCaptcha.setText(currentCaptcha);
        if (txtCaptchaInput != null) {
            txtCaptchaInput.setText("");
        }
    }

    private String sendRequest(String request) {
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

    private void setStatus(String message, Color color) {
        lblStatus.setText(message);
        lblStatus.setForeground(color);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SeckillClientWindow().setVisible(true);
        });
    }
}