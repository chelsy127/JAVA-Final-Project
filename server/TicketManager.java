package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TicketManager {
    private static final String STATE_FILE = "data/ticket-state.bin";
    private static final long ORDER_TIMEOUT_MS = parseOrderTimeoutMs();

    // 票種與單價（依插入順序提供給前端顯示）
    private static final Map<String, Integer> ticketPrices = new LinkedHashMap<>();
    // 各票種初始張數
    private static final Map<String, Integer> initialInventory = new LinkedHashMap<>();
    // 各票種剩餘張數
    private static final Map<String, AtomicInteger> ticketInventory = new ConcurrentHashMap<>();
    // 各票種累積售出票數
    private static final Map<String, AtomicInteger> soldByType = new ConcurrentHashMap<>();
    // 訂單資訊（流水號 -> 訂單）
    private static final Map<String, Order> orders = new ConcurrentHashMap<>();
    // 電話 -> 當前有效訂單（UNPAID 或 PAID），EXPIRED 會移除
    private static final Map<String, String> phoneToOrderId = new ConcurrentHashMap<>();
    private static final AtomicInteger orderSequence = new AtomicInteger(0);
    private static final AtomicInteger totalTicketsSold = new AtomicInteger(0);
    private static final AtomicInteger totalRevenue = new AtomicInteger(0);

    private static class Order implements Serializable {
        private static final long serialVersionUID = 1L;

        private enum Status {
            UNPAID,
            PAID,
            EXPIRED
        }

        private final String orderId;
        private final String userName;
        private final String phone;
        private final String ticketType;
        private final int quantity;
        private final int totalPrice;
        private final long createdAt;
        private long paidAt;
        private Status status;

        private Order(
                String orderId,
                String userName,
                String phone,
                String ticketType,
                int quantity,
                int totalPrice
        ) {
            this.orderId = orderId;
            this.userName = userName;
            this.phone = phone;
            this.ticketType = ticketType;
            this.quantity = quantity;
            this.totalPrice = totalPrice;
            this.createdAt = System.currentTimeMillis();
            this.paidAt = 0L;
            this.status = Status.UNPAID;
        }
    }

    private static class StateSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        Map<String, Integer> inventory = new LinkedHashMap<>();
        Map<String, Integer> sold = new LinkedHashMap<>();
        Map<String, Order> orders = new LinkedHashMap<>();
        Map<String, String> phoneOrderMap = new LinkedHashMap<>();
        int orderSeq;
        int totalSold;
        int revenue;
    }

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

        soldByType.put("VIP", new AtomicInteger(0));
        soldByType.put("A區", new AtomicInteger(0));
        soldByType.put("B區", new AtomicInteger(0));

        loadStateFromDisk();
        startExpiryCleaner();
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

        orders.clear();
        phoneToOrderId.clear();
        for (AtomicInteger sold : soldByType.values()) {
            sold.set(0);
        }
        totalTicketsSold.set(0);
        totalRevenue.set(0);
        orderSequence.set(0);

        saveStateToDisk();
        return "SUCCESS:系統已重置票況、訂單與購票紀錄";
    }

    public static synchronized String getAdminSummary() {
        int paidOrders = 0;
        int unpaidOrders = 0;
        int expiredOrders = 0;
        for (Order order : orders.values()) {
            if (order.status == Order.Status.PAID) {
                paidOrders++;
            } else if (order.status == Order.Status.UNPAID) {
                unpaidOrders++;
            } else {
                expiredOrders++;
            }
        }

        int vipSold = soldByType.get("VIP").get();
        int aSold = soldByType.get("A區").get();
        int bSold = soldByType.get("B區").get();

        return "ADMIN_SUMMARY:total_orders=" + orders.size()
                + ",paid_orders=" + paidOrders
                + ",unpaid_orders=" + unpaidOrders
                + ",expired_orders=" + expiredOrders
                + ",paid_tickets=" + totalTicketsSold.get()
                + ",paid_revenue=" + totalRevenue.get()
                + ",sold_vip=" + vipSold
                + ",sold_a=" + aSold
                + ",sold_b=" + bSold;
    }

    public static synchronized String getAdminOrders() {
        if (orders.isEmpty()) {
            return "ADMIN_ORDERS:EMPTY";
        }

        StringBuilder builder = new StringBuilder("ADMIN_ORDERS:\n");
        boolean first = true;
        for (int i = 1; i <= orderSequence.get(); i++) {
            String orderId = String.format("%04d", i);
            Order order = orders.get(orderId);
            if (order == null) {
                continue;
            }

            if (!first) {
                builder.append('\n');
            }
            builder.append(order.orderId)
                    .append('|').append(order.userName)
                    .append('|').append(order.phone)
                    .append('|').append(order.ticketType)
                    .append('|').append(order.quantity)
                    .append('|').append(order.totalPrice)
                    .append('|').append(order.status)
                    .append("|created=").append(order.createdAt)
                    .append("|paid=").append(order.paidAt);
            first = false;
        }

        return builder.toString();
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
        String existingOrderId = phoneToOrderId.get(phoneKey);
        if (existingOrderId != null) {
            Order existingOrder = orders.get(existingOrderId);
            if (existingOrder == null || existingOrder.status == Order.Status.EXPIRED) {
                phoneToOrderId.remove(phoneKey);
            } else if (existingOrder.status == Order.Status.PAID) {
                return "FAILED:此電話已完成購票，請勿重複下單";
            } else {
                return "FAILED:此電話已有待付款訂單，請先付款或等待逾時";
            }
        }

        int currentTickets = inventory.get();
        if (currentTickets < quantity) {
            return "FAILED:" + ticketType + "剩餘 " + currentTickets + " 張，無法購買 " + quantity + " 張";
        }

        int left = inventory.addAndGet(-quantity);

        int orderNo = orderSequence.incrementAndGet();
        int totalPrice = ticketPrices.get(ticketType) * quantity;
        String serial = String.format("%04d", orderNo);
        Order order = new Order(serial, userName.trim(), phoneKey, ticketType, quantity, totalPrice);
        orders.put(serial, order);
        phoneToOrderId.put(phoneKey, serial);

        System.out.println("【預訂成功】" + userName + "(" + phone + ") 預佔 " + ticketType + " " + quantity
                + " 張，訂單#" + serial + "，等待付款，剩餘: " + left);

        saveStateToDisk();

        return "SUCCESS:搶票成功！請於 " + (ORDER_TIMEOUT_MS / 1000)
                + " 秒內完成付款。訂單#" + serial + "，票種:" + ticketType + "，張數:" + quantity
                + "，總金額:$" + totalPrice + "，剩餘:" + left;
    }

    public static synchronized String payOrder(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return "FAILED:訂單編號不可為空";
        }

        Order order = orders.get(orderId.trim());
        if (order == null) {
            return "FAILED:找不到該筆訂單";
        }

        if (order.status == Order.Status.EXPIRED) {
            return "FAILED:該訂單已逾時失效，票券已釋回";
        }

        if (order.status == Order.Status.PAID) {
            return "SUCCESS:該訂單已完成付款，請勿重複付款";
        }

        order.status = Order.Status.PAID;
        order.paidAt = System.currentTimeMillis();
        soldByType.get(order.ticketType).addAndGet(order.quantity);
        totalTicketsSold.addAndGet(order.quantity);
        totalRevenue.addAndGet(order.totalPrice);

        saveStateToDisk();
        return "SUCCESS:訂單#" + order.orderId + " 付款成功！";
    }

    public static synchronized String queryOrderByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "FAILED:電話不可為空";
        }

        String phoneKey = phone.trim();
        Order order = findLatestOrderByPhone(phoneKey);
        if (order == null) {
            return "FAILED:找不到該電話的訂單";
        }

        return "QUERY_RES:"
                + order.orderId + "|"
                + order.userName + "|"
                + order.ticketType + "|"
                + order.quantity + "|"
                + order.totalPrice + "|"
                + order.status;
    }

    private static Order findLatestOrderByPhone(String phone) {
        String mappedOrderId = phoneToOrderId.get(phone);
        if (mappedOrderId != null) {
            Order mapped = orders.get(mappedOrderId);
            if (mapped != null) {
                return mapped;
            }
        }

        for (int i = orderSequence.get(); i >= 1; i--) {
            String id = String.format("%04d", i);
            Order order = orders.get(id);
            if (order != null && phone.equals(order.phone)) {
                return order;
            }
        }
        return null;
    }

    private static long parseOrderTimeoutMs() {
        String raw = System.getenv("SECKILL_ORDER_TIMEOUT_MS");
        if (raw == null || raw.trim().isEmpty()) {
            return 5L * 60L * 1000L;
        }

        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : 5L * 60L * 1000L;
        } catch (NumberFormatException ex) {
            return 5L * 60L * 1000L;
        }
    }

    private static void startExpiryCleaner() {
        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    long now = System.currentTimeMillis();

                    for (Order order : orders.values()) {
                        if (order.status != Order.Status.UNPAID) {
                            continue;
                        }

                        if (now - order.createdAt <= ORDER_TIMEOUT_MS) {
                            continue;
                        }

                        synchronized (TicketManager.class) {
                            if (order.status != Order.Status.UNPAID) {
                                continue;
                            }

                            order.status = Order.Status.EXPIRED;
                            AtomicInteger inventory = ticketInventory.get(order.ticketType);
                            if (inventory != null) {
                                inventory.addAndGet(order.quantity);
                            }

                            String mapped = phoneToOrderId.get(order.phone);
                            if (order.orderId.equals(mapped)) {
                                phoneToOrderId.remove(order.phone);
                            }

                            saveStateToDisk();
                            System.out.println("【自動釋票】訂單#" + order.orderId + " 逾時未付款，已釋回 "
                                    + order.ticketType + " " + order.quantity + " 張");
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "order-expiry-cleaner");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    private static void loadStateFromDisk() {
        Path path = Paths.get(STATE_FILE);
        if (!Files.exists(path)) {
            return;
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path))) {
            Object raw = in.readObject();
            if (!(raw instanceof StateSnapshot)) {
                return;
            }

            StateSnapshot snapshot = (StateSnapshot) raw;

            for (Map.Entry<String, Integer> entry : initialInventory.entrySet()) {
                String type = entry.getKey();
                int fallbackInventory = entry.getValue();

                int inventoryValue = snapshot.inventory.getOrDefault(type, fallbackInventory);
                int soldValue = snapshot.sold.getOrDefault(type, 0);

                ticketInventory.get(type).set(Math.max(0, inventoryValue));
                soldByType.get(type).set(Math.max(0, soldValue));
            }

            orders.clear();
            orders.putAll(snapshot.orders);

            phoneToOrderId.clear();
            phoneToOrderId.putAll(snapshot.phoneOrderMap);
            if (phoneToOrderId.isEmpty() && !orders.isEmpty()) {
                for (int i = 1; i <= snapshot.orderSeq; i++) {
                    String orderId = String.format("%04d", i);
                    Order order = orders.get(orderId);
                    if (order == null || order.status == Order.Status.EXPIRED) {
                        continue;
                    }
                    phoneToOrderId.put(order.phone, order.orderId);
                }
            }

            orderSequence.set(Math.max(0, snapshot.orderSeq));
            totalTicketsSold.set(Math.max(0, snapshot.totalSold));
            totalRevenue.set(Math.max(0, snapshot.revenue));
        } catch (Exception ex) {
            System.err.println("讀取票務狀態失敗，將使用預設初始值: " + ex.getMessage());
        }
    }

    private static void saveStateToDisk() {
        Path path = Paths.get(STATE_FILE);
        Path parent = path.getParent();

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }

            StateSnapshot snapshot = new StateSnapshot();
            for (String type : initialInventory.keySet()) {
                snapshot.inventory.put(type, ticketInventory.get(type).get());
                snapshot.sold.put(type, soldByType.get(type).get());
            }

            snapshot.orders.putAll(orders);
            snapshot.phoneOrderMap.putAll(phoneToOrderId);
            snapshot.orderSeq = orderSequence.get();
            snapshot.totalSold = totalTicketsSold.get();
            snapshot.revenue = totalRevenue.get();

            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(path))) {
                out.writeObject(snapshot);
            }
        } catch (IOException ex) {
            System.err.println("寫入票務狀態失敗: " + ex.getMessage());
        }
    }
}
