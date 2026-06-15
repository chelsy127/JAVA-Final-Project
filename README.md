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
│  ├─ SeckillAdminTool.java     # 終端機管理工具（main）
│  ├─ SeckillLoadTest.java      # 單機壓測腳本（main）
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
- `PAY|訂單號`
  - 用途：完成付款，將預約訂單轉為已付款
  - 回應格式：`SUCCESS:...` 或 `FAILED:...`
- `QUERY|電話`
  - 用途：依電話查詢最新訂單狀態
  - 回應格式：`QUERY_RES:訂單號|姓名|票種|張數|總價|狀態`
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
- `PAY|orderId`
- `QUERY|phone`

預設 token 為 `ncku-admin`，可透過環境變數 `SECKILL_ADMIN_TOKEN` 覆蓋。

## 開發環境

- JDK 8+（建議 JDK 17）
- Windows PowerShell（或任何可執行 `javac/java` 的終端機）

## 如何執行

### 1) 編譯

在專案根目錄執行：

```powershell
cd c:\c\.vscode\JAVA\JAVA-Final-Project
javac .\client\SeckillAdminTool.java .\client\SeckillLoadTest.java .\client\SeckillServer.java .\server\ClientHandler.java .\server\SeckillClientWindow.java .\server\TicketManager.java
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

### 3.5) 管理/查單工具（v3）

編譯後可在終端機執行：

```powershell
java client.SeckillAdminTool SUMMARY
java client.SeckillAdminTool ORDERS
java client.SeckillAdminTool PAY 0001
java client.SeckillAdminTool QUERY 0912345678
```

指定 token：

```powershell
java client.SeckillAdminTool SUMMARY myToken
java client.SeckillAdminTool ORDERS myToken
```

用途：
- `SUMMARY`：看目前總訂單、總售出票數、總營收與分區售票數
- `ORDERS`：看每一筆訂單明細（訂單號、姓名、電話、票種、張數、總價、時間戳）
- `PAY`：對指定訂單號補付款（將 `UNPAID` 轉為 `PAID`）
- `QUERY`：依電話查詢最新訂單狀態

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
- `CSV`：輸出壓測結果到預設檔案 `reports/loadtest-results-v3.csv`
- `CSV=<路徑>`：輸出到指定 CSV 路徑
- `PAYRATE=<0~100>`：成功預約後實際付款比例（預設 `100`）

CSV 範例：

```powershell
java client.SeckillLoadTest 120 30 RANDOM RESET CSV
java client.SeckillLoadTest 120 30 VIP RESET CSV=reports/v2-vip.csv
java client.SeckillLoadTest 120 30 RANDOM RESET PAYRATE=70 CSV=reports/v3-mixed.csv
```

輸出會包含：
- 成功/失敗總數
- 付款成功數 / 未付款保留數
- 訂單查詢成功數 / 查詢不一致數
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

## v3 重點（付款與查單）

- 訂單生命週期：`UNPAID -> PAID`，逾時未付款會自動變成 `EXPIRED` 並釋回庫存。
- 前台新協定：`PAY|訂單號`、`QUERY|電話`。
- 訂單查詢：可查詢電話對應的最新訂單與狀態。
- 壓測升級：可用 `PAYRATE` 模擬「只預約不付款」場景，驗證逾時釋票與查單流程。

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

## 版本演進表

| 版本 | Tag | 更新重點 |
| --- | --- | --- |
| 初始版本 | `7abc822`（無 tag） | 建立最初的 Socket + Swing 搶票雛形 |
| 基線版本 | `v0-baseline` | 記錄 AI 優化前的原始狀態 |
| v1.0 | `v1-round1-ui-upgrade` | 改為兩步驟搶票流程、多票種分區、驗證碼與剩餘票數顯示 |
| v1.1 | `v1.1` | 修正第二頁表單跑位，驗證碼可點擊刷新 |
| v1.2 | `v1.2` | 進入第二次購票時自動清空資料，張數改為下拉選單 |
| v1.3 | `v1.3` | 新增單機壓測腳本，可模擬多人併發搶票 |
| v1.4 | `v1.4` | 新增 RESET 指令，可不重啟 server 重置票況與購票紀錄 |
| v2.0.0-rc1 | `v2.0.0-rc1` | 壓測結果可輸出 CSV，方便做圖表與實驗分析 |
| v2.0.0-rc2 | `v2.0.0-rc2` | 新增 ADMIN 查詢指令與管理端工具，統計訂單與營收 |
| v2.0.0 | `v2.0.0` | 加入持久化、管理指令 token 驗證，完成正式版發行 |
| v3.0.0 | `v3.0.0` | 新增付款/查單、訂單逾時釋票與壓測付款比例模擬 |

## v3 未來方向

如果之後真的要再往下做，我會把 v3 定位成「更接近正式系統」的版本，而不是單純再加幾個功能而已。比較合理的方向如下：

- 資料庫化：把 `data/ticket-state.bin` 改成真正的資料庫儲存，讓訂單、庫存與操作紀錄可以更穩定查詢與維護。
- 權限分級：把現在的單一 token 機制擴充成管理者 / 使用者的角色權限，讓後台操作可以更細緻地控制。
- 可觀測性：加入更完整的日誌、時間戳、失敗原因分類與統計圖表，方便課堂報告以外的分析。
- 測試完整化：補上單元測試、整合測試與更大規模的併發壓測，驗證高流量下的穩定性。
- 部署現代化：把桌面版與伺服器端的啟動流程整理成更標準的部署方式，降低手動操作成本。

就這份專案目前的完成度來看，v2 已經可以當作正式收尾版；v3 比較適合留給真的要把它延伸成完整系統時再做。

## 授權

本專案為課程期末報告示範用途。