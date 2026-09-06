# Privacy Display — Expanded Android Implementation

This iteration adds patterned overlays, image obfuscation, and a Google Play Billing integration to the initial Privacy Display prototype. Release metadata is included as preparation material, not proof of store acceptance.

## Configuration

| Setting | Value in source |
|---|---|
| Application ID | `tw.privacylab.privacydisplay` |
| Version | `1.1.1` / code `10101` |
| Minimum / target / compile SDK | `28` / `35` / `36` |
| Gradle / Android Gradle Plugin | `9.3.1` / `9.1.1` |
| Billing library | `8.3.0` |

These are repository settings, not a statement about current store submission requirements.

## Source guide

Java source is under `app/src/main/java/tw/privacylab/privacydisplay/`:

- `MainActivity`: consent, permissions, selected applications, and settings.
- `PrivacyOverlayService`: full-screen and patterned overlay rendering.
- `PrivacyNotificationListenerService`: selected-notification handling.
- `PrivacyTileService`: Quick Settings integration.
- `ImagePrivacyActivity` and `ImagePrivacyProcessor`: image selection, local filters, and export.
- `BillingManager`: one-time purchase and entitlement flow.
- `SettingsStore`: local preferences.

## Debug build

Configure Android SDK API 36 locally, then run:

```bash
./gradlew :app:assembleDebug
```

Windows: `.\gradlew.bat :app:assembleDebug`. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Release and billing setup

Copy `keystore.properties.sample` to the ignored `keystore.properties`, enter your own key configuration, and run `./gradlew :app:bundleRelease`. Never commit the populated properties or signing key. An unsigned build is not a distributable store release.

The billing integration expects the non-consumable product ID `privacy-image-filter-pro`. It requires configuration and testing in the corresponding Play Console account; source code alone cannot verify purchases or store availability.

## Documentation and evidence

`docs/play_console/`, `docs/legal/`, `fastlane/metadata/`, and `store_assets/` contain original Traditional Chinese release drafts and promotional assets. Reconcile them with the actual build before publication. Software overlays and image filters cannot guarantee hardware-level privacy.

See [validation scope](../VALIDATION.md), [publication notes](../PUBLICATION.md), and the [project overview](../README.md).
