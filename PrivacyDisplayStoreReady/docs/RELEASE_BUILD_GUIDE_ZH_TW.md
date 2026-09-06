# Release / AAB 建置指南

## 1. 必要環境

- JDK 17 或以上。本機已使用 JDK 21 驗證。
- Android SDK Platform 36。本專案 `compileSdk 36`、`targetSdk 35`。
- Gradle wrapper：專案已內建 `gradlew` / `gradlew.bat`，會下載 Gradle 9.3.1。

Windows PowerShell 範例：

```powershell
$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
```

## 2. 建立 Google Play Upload Key

正式上架前請自行保存 upload key，不要提交到版本庫。

```bash
keytool -genkeypair -v \
  -keystore privacy-display-upload-key.jks \
  -alias privacy_display_upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

## 3. 建立 `keystore.properties`

複製範本：

```bash
cp keystore.properties.sample keystore.properties
```

內容範例：

```properties
storeFile=privacy-display-upload-key.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=privacy_display_upload
keyPassword=YOUR_KEY_PASSWORD
```

`keystore.properties` 和 `.jks` 已在 `.gitignore` 中，請不要公開。

## 4. 建置 Debug APK

```bash
./gradlew :app:assembleDebug
```

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
```

輸出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 5. 建置 Release AAB

```bash
./gradlew :app:bundleRelease
```

Windows：

```powershell
.\gradlew.bat :app:bundleRelease
```

輸出位置：

```text
app/build/outputs/bundle/release/app-release.aab
```

## 6. Play Console 上架檢查

1. 建立 App，Package name 使用 `tw.privacylab.privacydisplay`。
2. 上傳 `app-release.aab` 到 Internal testing。
3. 填寫 Store listing、Data Safety、App content、Content rating、Target audience。
4. 將 `docs/legal/privacy_policy_zh-TW.html` 部署到公開網址，填入 Privacy Policy URL。
5. 建立一次性商品 `privacy_image_filter_pro`，並先在測試軌啟用。
6. 使用測試帳號從 Play 安裝，測試權限流程、Overlay、通知防窺、圖片轉檔與購買恢復。
7. 審查若要求影片，使用 `docs/play_console/REVIEW_VIDEO_SCRIPT_ZH_TW.md` 錄製。

## 7. 已驗證結果

本工作區已完成：

```text
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:bundleRelease
```

兩者皆建置成功。若未設定 keystore，Release AAB 不能作為正式簽章版本；請填入 upload key 後重新建置。
