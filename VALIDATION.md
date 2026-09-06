# Validation Record

Checked on 2026-09-06 against this public source snapshot.

| Version | Command | Result |
|---|---|---|
| Expanded implementation | `PrivacyDisplayStoreReady/gradlew.bat :app:assembleDebug --no-daemon --max-workers=2`, from that directory | Passed; 33 tasks executed |
| Initial prototype | `../PrivacyDisplayStoreReady/gradlew.bat -p . :app:assembleDebug --no-daemon --max-workers=2`, from PrivacyDisplayApp | Passed; 33 tasks executed |

Environment: Windows, JDK 21, Android SDK API 36, Gradle 9.3.1, and Android Gradle Plugin 9.1.1. Both debug APKs were produced from the source in an isolated import working directory.

The bundled wrapper JAR matched the [official Gradle checksum reference](https://gradle.org/release-checksums/) for version 9.3.1. The builds reported Gradle deprecation warnings; compatibility with a future major Gradle release was not tested.

## Limits

This run did not install the APKs on a physical device, measure privacy effectiveness, validate notification behavior across devices, execute billing transactions, build a signed production release, or submit to Google Play. Successful debug builds establish compilation and packaging only.

The original release upload key and private signing properties are excluded. Promotional images and historical submission drafts do not establish tested outcomes or store approval.
