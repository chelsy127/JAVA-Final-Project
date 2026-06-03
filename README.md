# JAVA Final Project - 搶票系統（Seckill Simulation）

這是一個以 Java Socket + Swing 實作的「搶票模擬系統」。

系統包含：
- 中央伺服器：處理多使用者並發搶票請求
- 視窗化客戶端：使用者輸入名稱後發送搶票請求
- 執行緒安全票務核心：控制票數、避免重複購買

## 專案目錄

```text
JAVA-Final-Project/
├─ client/
│  └─ SeckillServer.java        # 伺服器主程式（main）
└─ server/
   ├─ SeckillClientWindow.java  # Swing 客戶端主程式（main）
   ├─ ClientHandler.java        # 每個連線的請求處理器
   └─ TicketManager.java        # 票務與搶票邏輯
```

## 系統設計概念

- 通訊模式：TCP Socket（短連線）
- 伺服器並發：固定大小執行緒池（20 threads）
- 共享資料保護：
  - `AtomicInteger` 管理剩餘票數
  - `ConcurrentHashMap` 記錄成功名單
  - `synchronized` 保證搶票流程原子性

## 功能說明

- 初始票數：10 張
- 客戶端送出使用者 ID 後，伺服器回傳：
  - `SUCCESS:...` 搶票成功
  - `FAILED:...` 失敗（重複購買或票已售罄）
- 客戶端介面會即時顯示結果並提示訊息

## 開發環境

- JDK 8+（建議 JDK 17）
- Windows PowerShell（或任何可執行 `javac/java` 的終端機）

## 如何執行

### 1) 編譯

在專案根目錄執行：

```powershell
cd c:\c\.vscode\JAVA\JAVA-Final-Project
javac .\client\SeckillServer.java .\server\ClientHandler.java .\server\SeckillClientWindow.java .\server\TicketManager.java
```

### 2) 啟動伺服器

開一個終端機：

```powershell
cd c:\c\.vscode\JAVA\JAVA-Final-Project
java client.SeckillServer
```

若啟動成功，會看到：

```text
=== 搶票中央伺服器已啟動，監聽 Port: 8888 ===
```

### 3) 啟動客戶端

再開另一個終端機：

```powershell
cd c:\c\.vscode\JAVA\JAVA-Final-Project
java server.SeckillClientWindow
```

可開多個客戶端視窗模擬多人同時搶票。

## 網路設定

- 目前客戶端預設連線：`127.0.0.1:8888`
- 若要跨電腦測試，請修改 `SeckillClientWindow.java` 的 `SERVER_IP` 為伺服器主機區網 IP（例如 `192.168.x.x`）

## Git 記錄建議（期末報告用）

建議保留以下里程碑：

- `feat: initial commit ...`：初始版本
- `chore: baseline snapshot before optimization phase`：優化前基線
- `v0-baseline` tag：標記優化前狀態
- `exp/optimization-round1` 分支：開始進行優化與實驗

後續每輪優化可用：
- `feat:` 新增功能
- `refactor:` 重構不改行為
- `perf:` 效能優化
- `fix:` 修正錯誤
- `docs:` 文件更新

## 後續可優化方向

- 將票務資料持久化（檔案或資料庫）
- 增加請求時間戳與操作日誌
- 加入更細緻的錯誤碼與例外處理
- 補齊測試（單元測試、併發壓力測試）

## 授權

本專案為課程期末報告示範用途。