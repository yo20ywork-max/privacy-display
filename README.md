# Privacy Display — Android Privacy Overlay

A native Android application exploring user-controlled screen overlays, per-app activation, notification redaction, and local image obfuscation.

**By Daniel Tang.** This repository preserves two iterations of the same project, making the development progression available for review.

## Project versions

| Directory | Implementation scope | Android package |
|---|---|---|
| [`PrivacyDisplayApp`](PrivacyDisplayApp) | Initial overlay, app selection, notification handling, and Quick Settings tile | `com.example.privacydisplay` |
| [`PrivacyDisplayStoreReady`](PrivacyDisplayStoreReady) | Expanded visual filters, image processing, billing integration, and release preparation | `tw.privacylab.privacydisplay` |

Start with **PrivacyDisplayStoreReady** for the more developed implementation. Its original directory name describes a release-preparation workstream; it does not establish Google Play approval or production readiness.

## Engineering work

- A foreground service renders an adjustable overlay above eligible application windows.
- Usage Access supports automatic activation for selected foreground apps.
- A notification listener can temporarily activate the overlay and optionally replace selected notifications with a generic notice.
- A Quick Settings tile provides a direct toggle.
- The later version adds locally processed image filters and a Google Play Billing integration for a one-time product.
- Settings and consent flows make privileged Android capabilities visible to the user.

These are software visual-obfuscation techniques. They do not change a display's physical viewing angle, and protection across system screens or devices is not guaranteed. See the source and permission behavior before enabling sensitive access.

## Build the main version

Install an Android SDK containing API 36 and a compatible JDK. The supplied wrapper pins Gradle 9.3.1; the project uses Android Gradle Plugin 9.1.1. See [AGP compatibility requirements](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

```bash
cd PrivacyDisplayStoreReady
./gradlew :app:assembleDebug
```

On Windows, use `.\gradlew.bat :app:assembleDebug`. Set your SDK location in Android Studio or a local `local.properties` file. The debug APK is written to `app/build/outputs/apk/debug/`.

Release signing requires your own upload key and a private `keystore.properties` file based on the included sample. Both real signing keys and private properties are excluded from this repository. See the [main version README](PrivacyDisplayStoreReady/README.md) for billing and packaging details.

## Evidence and limitations

[Validation scope](VALIDATION.md) records checks performed on this public snapshot. Physical-device behavior, purchase flows, and store acceptance need separate verification.

[`store_assets`](PrivacyDisplayStoreReady/store_assets) and `fastlane/metadata` contain original promotional mockups and store drafts, not measured privacy results. Some material predates the later billing integration; it should be reviewed before reuse in a store listing. The original Traditional Chinese interface and submission documents are retained; all READMEs are in English.

See [publication notes](PUBLICATION.md). Android, AndroidX, Gradle, and Google Play Billing are third-party components with their own terms and licenses.

[Daniel Tang's software and AI portfolio](https://github.com/yo20ywork-max/research-portfolio)
