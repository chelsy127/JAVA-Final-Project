package server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TicketManager {
    // 記憶體內的票數計算機，AtomicInteger保證執行緒安全
    private static final AtomicInteger remainingTickets = new AtomicInteger(10); // 總共只有10張票
    // 記錄誰搶成功了，使用執行緒安全的 ConcurrentHashMap
    private static final Map<String, Boolean> successRecords = new ConcurrentHashMap<>();

    public static synchronized String tryToBook(String userId) {
        // 1. 檢查是否重複購買
        if (successRecords.containsKey(userId)) {
            return "FAILED:重複購買";
        }

        // 2. 檢查並扣減票數
        int currentTickets = remainingTickets.get();
        if (currentTickets <= 0) {
            return "FAILED:票已被搶光！";
        }

        // 扣票 (原子操作)
        remainingTickets.decrementAndGet();
        successRecords.put(userId, true);
        System.out.println("【成功】使用者 " + userId + " 搶票成功！剩餘票數: " + remainingTickets.get());
        return "SUCCESS:搶票成功！您目前的序號為 No." + (10 - remainingTickets.get());
    }
}
