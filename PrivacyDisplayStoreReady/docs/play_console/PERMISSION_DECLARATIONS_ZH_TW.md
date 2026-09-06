# Play Console 權限聲明 zh-TW

以下內容可作為 Play Console 權限審查、App content 或審查備註使用。正式送審前請依 Console 當下欄位調整。

## SYSTEM_ALERT_WINDOW

用途：提供使用者主動啟用的防窺遮罩。當使用者開啟全螢幕防窺、指定 App 防窺，或指定 App 通知觸發防窺時，App 會以透明/半透明 Overlay 覆蓋螢幕，降低旁人看到畫面內容的機會。

使用者控制：使用者必須先在 Android 系統設定授權「顯示在其他 App 上層」，並在 App 內明確開啟防窺模式。使用者可在 App 內停止所有防窺功能，也可移除系統權限。

資料處理：Overlay 僅在本機顯示，不讀取、不錄製、不上傳其他 App 畫面內容。

## PACKAGE_USAGE_STATS

用途：在「指定 App 防窺」模式下，本機判斷目前前景 App 是否為使用者勾選的 App。若符合，才顯示防窺遮罩。

使用者控制：使用者必須先在 Android 使用情況存取權設定中授權，並自行勾選要保護的 App。

資料處理：只在本機判斷套件名稱，不上傳使用紀錄或 App 清單。

## BIND_NOTIFICATION_LISTENER_SERVICE

用途：在「指定 App 通知防窺」模式下，本機判斷使用者勾選的 App 是否送出通知，並依設定短暫啟用遮罩。使用者可選擇移除原通知並改顯示一般提醒。

使用者控制：使用者必須先在 Android 通知存取權設定中授權，並在 App 內明確開啟通知防窺。

資料處理：通知判斷在本機完成，不上傳通知內容。

## POST_NOTIFICATIONS

用途：在 Android 13 以上顯示前景服務通知與替代提醒。

資料處理：不涉及資料上傳。

## FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE

用途：讓防窺遮罩服務在使用者啟用功能後持續運作，並透過常駐通知告知使用者服務正在執行。

特殊用途描述：User initiated privacy overlay to protect screen content and monitor selected foreground apps locally.

## com.android.vending.BILLING

用途：提供「圖片防窺轉檔 Pro」一次性付費解鎖。

資料處理：付款與帳務由 Google Play 處理，App 只取得商品與購買狀態。
