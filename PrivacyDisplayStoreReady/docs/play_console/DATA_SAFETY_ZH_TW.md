# Google Play Data Safety 建議填寫 zh-TW

> 請以 Play Console 當下表單為準。以下依目前 App 實作整理。

## 是否收集或分享使用者資料

建議回答：否。

理由：

- App 不宣告 `INTERNET` 權限。
- App 清單、使用情況判斷、通知判斷、遮罩設定與圖片處理都在裝置本機完成。
- App 不將圖片、通知內容、使用紀錄或 App 清單傳送到開發者伺服器。

## 第三方 SDK / 服務

- Google Play Billing：用於一次性付費商品 `privacy-image-filter-pro`。
- Google Play Billing 由 Google Play 處理付款流程。App 只取得商品資訊、購買狀態與授權狀態。

## 安全性

- 不使用網路傳輸使用者資料。
- 圖片透過 Android 文件選取器讀取，轉換後只儲存在使用者選擇的位置。
- 使用者可透過 Android 系統設定清除 App 資料或解除安裝 App。

## Play Console 可貼文字

Privacy Display does not collect or share user data with the developer. The app does not request INTERNET permission. App selections, usage-access checks, notification-listener checks, overlay settings, and image conversion are processed locally on the device. Google Play Billing is used only to determine purchase entitlement for a one-time product.

