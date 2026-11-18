# WalkTrack GPS 2.0 — Android 原生版

針對 Samsung Galaxy S23 Ultra 的 Kotlin Android 專案。使用 OpenStreetMap（osmdroid）和 Google Play Services Fused Location Provider。

## 功能
- 走路／慢跑、距離、運動時間、平均配速、熱量估算
- Android location foreground service：螢幕鎖定後持續請求 GPS 定位（仍受系統與訊號限制）
- 即時地圖以第一個 GPS 定位點為中心，預設約 2 公里視野；可切換 1／2／3 公里
- 自動跟隨、拖曳後暫停跟隨、一鍵回到定位
- GPS 精度超過 35 m 的點排除；超過 15 秒的定位間隔分段，避免錯誤直線
- SQLite 本機歷史紀錄、週統計、GPX 1.1 匯出（分段 trkseg）

## 電腦編譯 APK（建議）
1. 安裝 Android Studio（2024.3 或更新版本），並安裝 Android SDK Platform 35、Build Tools、JDK 17。
2. 解壓縮此專案，Android Studio 選 **Open**，選擇 `WalkTrackGPS2` 資料夾。
3. 等待 Gradle Sync；需連線下載 Android Gradle Plugin、Kotlin、osmdroid 和 Play Services。
4. 選單 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。
5. APK 通常位於 `app/build/outputs/apk/debug/app-debug.apk`。
6. 將 APK 傳到 S23 Ultra，用「我的檔案」開啟安裝；Android 可能要求允許該來源安裝未知應用程式。
7. 開啟 App，允許精確定位和通知。**先在 App 開啟時按開始**，再鎖定螢幕。
8. Samsung 設定 > 應用程式 > WalkTrack GPS > 電池 > 不受限制（各版本文字可能不同）；定位請保持開啟。

> 這份交付是原始碼專案，不是已編譯或已在 S23 Ultra 驗證的 APK。本環境沒有 Android SDK/Gradle，無法在此完成 Android 編譯或手機實測。

## 使用注意
- Android 前景服務在通知列顯示 GPS 正在記錄；不需要「一律允許」背景定位權限，因為追蹤由使用者在 App 前景主動啟動。
- 不會補出 GPS 中斷期間的真實路線；分段可避免虛假直線。
- 熱量是簡化估算（走路約 0.5、慢跑約 1 kcal/kg/km），不是醫療或精密運動測量。
- 本機資料不會自動從舊網頁版匯入；舊網頁版歷史紀錄仍在原瀏覽器儲存空間。可先從舊版匯出 GPX 備份。
- OpenStreetMap 瓦片需要網路；不要大量下載圖磚。GPS 記錄不依賴圖磚網路。
- 追蹤中若系統強制終止 App、重啟手機，這個第一版不會自動恢復前景服務；已寫入的點仍保留於 SQLite，但未結束的紀錄不會顯示在歷史清單。
