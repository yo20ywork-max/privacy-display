# 更新紀錄

## 1.1.0 — 圖片防窺轉換 Pro

新增：

- 付費功能入口「圖片防窺轉換 Pro」。
- Google Play Billing 一次性內購商品：`privacy_image_filter_pro`。
- 還原購買流程。
- 支援透過 Android 文件選擇器匯入圖檔。
- 使用 `ImageDecoder` 解碼 Android 系統可支援的圖片格式，例如 HEIC / HEIF、PNG、JPG / JPEG、WebP、GIF、BMP、AVIF 等。
- 防窺視覺轉換演算法：亮度分層、仿防窺片直紋、細節降低、微雜訊與邊緣遮蔽。
- 輸出格式：PNG、JPG / JPEG、WebP、HEIC / HEIF。
- 上架文件補充：內購商品設定、Data Safety、權限聲明、審查影片腳本、QA 測試案例。

調整：

- 版本號從 `1.0.0` 調整為 `1.1.0`。
- `versionCode` 從 `10000` 調整為 `10100`。
- `minSdk` 從 26 調整為 28，以支援 `ImageDecoder` 的現代圖檔解碼流程。
