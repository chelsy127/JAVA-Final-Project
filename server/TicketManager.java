package server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TicketManager {
    // 票種與單價（依插入順序提供給前端顯示）
    private static final Map<String, Integer> ticketPrices = new LinkedHashMap<>();
    // 各票種初始張數
    private static final Map<String, Integer> initialInventory = new LinkedHashMap<>();
    // 各票種剩餘張數
    private static final Map<String, AtomicInteger> ticketInventory = new ConcurrentHashMap<>();
    // 紀錄使用者電話是否已成功購票（避免重複購買）
    private static final Map<String, Integer> successRecords = new ConcurrentHashMap<>();
    private static final AtomicInteger orderSequence = new AtomicInteger(0);

    static {
        ticketPrices.put("VIP", 3800);
        ticketPrices.put("A區", 2600);
        ticketPrices.put("B區", 1800);

        initialInventory.put("VIP", 6);
        initialInventory.put("A區", 12);
        initialInventory.put("B區", 20);

        ticketInventory.put("VIP", new AtomicInteger(initialInventory.get("VIP")));
        ticketInventory.put("A區", new AtomicInteger(initialInventory.get("A區")));
        ticketInventory.put("B區", new AtomicInteger(initialInventory.get("B區")));
    }

    public static synchronized String resetState() {
        for (Map.Entry<String, Integer> entry : initialInventory.entrySet()) {
            String type = entry.getKey();
            int value = entry.getValue();

            AtomicInteger inventory = ticketInventory.get(type);
            if (inventory == null) {
                ticketInventory.put(type, new AtomicInteger(value));
            } else {
                inventory.set(value);
            }
        }

        successRecords.clear();
        orderSequence.set(0);
        return "SUCCESS:系統已重置票況與購票紀錄";
    }

    public static synchronized String getTicketStatus() {
        StringBuilder builder = new StringBuilder("STATUS:");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : ticketPrices.entrySet()) {
            String type = entry.getKey();
            int price = entry.getValue();
            int remaining = ticketInventory.get(type).get();
            if (!first) {
                builder.append(',');
            }
            // 格式：票種=剩餘@單價
            builder.append(type).append('=').append(remaining).append('@').append(price);
            first = false;
        }
        return builder.toString();
    }

    public static synchronized String tryToBook(String userName, String phone, String ticketType, int quantity) {
        if (userName == null || userName.trim().isEmpty()) {
            return "FAILED:姓名不可為空";
        }

        if (phone == null || phone.trim().isEmpty()) {
            return "FAILED:電話不可為空";
        }

        if (quantity <= 0) {
            return "FAILED:張數需大於 0";
        }

        if (quantity > 4) {
            return "FAILED:單次最多購買 4 張";
        }

        AtomicInteger inventory = ticketInventory.get(ticketType);
        if (inventory == null) {
            return "FAILED:未知票種";
        }

        String phoneKey = phone.trim();
        if (successRecords.containsKey(phoneKey)) {
            return "FAILED:此電話已完成購票，請勿重複下單";
        }

        int currentTickets = inventory.get();
        if (currentTickets < quantity) {
            return "FAILED:" + ticketType + "剩餘 " + currentTickets + " 張，無法購買 " + quantity + " 張";
        }

        int left = inventory.addAndGet(-quantity);
        successRecords.put(phoneKey, quantity);

        int orderNo = orderSequence.incrementAndGet();
        int totalPrice = ticketPrices.get(ticketType) * quantity;
        String serial = String.format("%04d", orderNo);

        System.out.println("【成功】" + userName + "(" + phone + ") 購買 " + ticketType + " " + quantity
                + " 張，訂單#" + serial + "，剩餘: " + left);

        return "SUCCESS:訂票成功！訂單#" + serial + "，票種:" + ticketType + "，張數:" + quantity
                + "，總金額:$" + totalPrice + "，剩餘:" + left;
    }
}
