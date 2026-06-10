# JAVA Final Project - 搶票系統（Seckill Simulation）

這是一個以 Java Socket + Swing 實作的「搶票模擬系統」。

系統包含：
- 中央伺服器：處理多使用者並發搶票請求
- 視窗化客戶端：兩步驟 UI（先選票種，再填表單送單）
- 執行緒安全票務核心：多票種、庫存查詢、避免重複購買

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

- 多票種與分區：`VIP`、`A區`、`B區`（各自庫存與票價）
- 兩階段購票流程：
  1) 先選票種並查看剩餘張數
  2) 進入表單填寫姓名、電話、張數與驗證碼
- 伺服器回傳格式：
  - `SUCCESS:...` 搶票成功
  - `FAILED:...` 失敗（重複購買、票不足、格式錯誤）
- 客戶端可隨時「重新整理票況」查看各票種剩餘量

## Client/Server 協定

- `STATUS`
  - 用途：查詢所有票種庫存與價格
  - 回應格式：`STATUS:票種=剩餘@單價,票種=剩餘@單價...`
- `BOOK|票種|姓名|電話|張數`
  - 用途：送出購票請求
  - 回應格式：`SUCCESS:...` 或 `FAILED:...`
- `RESET`
  - 用途：重置伺服器票況與已購買紀錄（需 token）
  - 回應格式：`SUCCESS:...`
- `ADMIN|SUMMARY`
  - 用途：查詢後台統計（需 token）
  - 回應格式：`ADMIN_SUMMARY:...`
- `ADMIN|ORDERS`
  - 用途：查詢所有訂單明細（需 token）
  - 回應格式：`ADMIN_ORDERS:...`

實際請求格式：
- `RESET|token`
- `ADMIN|SUMMARY|token`
- `ADMIN|ORDERS|token`

預設 token 為 `ncku-admin`，可透過環境變數 `SECKILL_ADMIN_TOKEN` 覆蓋。

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

### 3.5) 管理端查詢工具（rc2）

編譯後可在終端機執行：

```powershell
java client.SeckillAdminTool SUMMARY
java client.SeckillAdminTool ORDERS
```

指定 token：

```powershell
java client.SeckillAdminTool SUMMARY myToken
java client.SeckillAdminTool ORDERS myToken
```

用途：
- `SUMMARY`：看目前總訂單、總售出票數、總營收與分區售票數
- `ORDERS`：看每一筆訂單明細（訂單號、姓名、電話、票種、張數、總價、時間戳）

### 4) 單機壓測腳本（不開 UI 也能測多人搶票）

如果你只有一台電腦，可以用壓測程式一次模擬多個客戶端連線。

先編譯：

```powershell
cd c:\c\.vscode\JAVA\JAVA-Final-Project
javac .\client\SeckillLoadTest.java
```

執行（範例）：

```powershell
java client.SeckillLoadTest 80 20 RANDOM
```

測試前先自動重置（建議）：

```powershell
java client.SeckillLoadTest 80 20 RANDOM RESET
```

只執行重置指令：

```powershell
java client.SeckillLoadTest RESET
```

參數說明：
- 第 1 個參數：模擬使用者數（預設 60）
- 第 2 個參數：執行緒數（預設 20）
- 第 3 個參數：票種模式（`RANDOM` / `VIP` / `A區` / `B區`，預設 `RANDOM`）
- 第 4 個參數：`RESET`（可選；代表壓測前先重置伺服器狀態）

進階旗標（可從第 4 個參數起混用）：
- `CSV`：輸出壓測結果到預設檔案 `reports/loadtest-results.csv`
- `CSV=<路徑>`：輸出到指定 CSV 路徑

CSV 範例：

```powershell
java client.SeckillLoadTest 120 30 RANDOM RESET CSV
java client.SeckillLoadTest 120 30 VIP RESET CSV=reports/v2-vip.csv
```

輸出會包含：
- 成功/失敗總數
- 失敗原因統計
- 平均延遲與 P95 延遲
- 測試前後票況（`STATUS`）

## v2 實驗建議流程

建議每組測試都先 reset，再輸出 CSV：

```powershell
java client.SeckillLoadTest RESET
java client.SeckillLoadTest 60 20 RANDOM RESET CSV=reports/exp-round1.csv
java client.SeckillLoadTest 120 30 RANDOM RESET CSV=reports/exp-round1.csv
java client.SeckillLoadTest 200 40 RANDOM RESET CSV=reports/exp-round1.csv
```

若你有自訂 token，可在旗標補上 `TOKEN=<token>`：

```powershell
java client.SeckillLoadTest 120 30 RANDOM RESET CSV TOKEN=myToken
java client.SeckillLoadTest RESET myToken
```

你可以直接把 `reports/*.csv` 當成期末報告的數據附件，做趨勢圖（Users 對 Success/Failed、P95 latency）。

## 網路設定

- 目前客戶端預設連線：`127.0.0.1:8888`
- 若要跨電腦測試，請修改 `SeckillClientWindow.java` 的 `SERVER_IP` 為伺服器主機區網 IP（例如 `192.168.x.x`）

## v2 正式版重點

- 持久化：伺服器會把票務/訂單狀態寫入 `data/ticket-state.bin`，重啟後可恢復。
- 後台安全：RESET 與 ADMIN 指令需要 token 才可操作。

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