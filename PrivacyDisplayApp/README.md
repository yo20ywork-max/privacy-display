# Privacy Display — Initial Android Prototype

This is the first implementation of the Privacy Display application. It uses native Java and Android services to explore adjustable screen overlays, per-app activation, and notification privacy.

## Implemented components

| File | Responsibility |
|---|---|
| `MainActivity.java` | Permissions, settings, and application selection |
| `PrivacyOverlayService.java` | Overlay rendering and foreground-app monitoring |
| `PrivacyNotificationListenerService.java` | Selected-notification handling |
| `PrivacyTileService.java` | Quick Settings toggle |
| `SettingsStore.java` | Local preferences |

Sources are under `app/src/main/java/com/example/privacydisplay/`.

## Build

Open this directory in Android Studio with Android SDK API 36 installed. Alternatively, reuse the pinned wrapper from the later iteration:

```bash
../PrivacyDisplayStoreReady/gradlew -p . :app:assembleDebug
```

On Windows, use `..\PrivacyDisplayStoreReady\gradlew.bat -p . :app:assembleDebug`. Configure your own SDK path locally. See the repository's [validation record](../VALIDATION.md) for the checks actually performed.

## First use

Grant only the capabilities you want to test: display over other apps, Usage Access for per-app activation, notification access for notification handling, and notification permission where required. Select protected applications and enable the relevant switches. Permissions can be revoked in Android settings.

## Limitations and evolution

The overlay cannot alter display hardware or guarantee coverage of lock screens, permission dialogs, or protected system surfaces. Background restrictions and notification cancellation behavior vary by device.

The expanded [`PrivacyDisplayStoreReady`](../PrivacyDisplayStoreReady) iteration adds visual filters, image processing, and billing integration. This prototype is preserved as part of the same project's development history.
