package tw.privacylab.privacydisplay;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class PrivacyTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!SettingsStore.prefs(this).getBoolean(SettingsStore.KEY_CONSENT_GRANTED, false)) {
            Intent openIntent = new Intent(this, MainActivity.class);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    PendingIntent pendingIntent = PendingIntent.getActivity(
                            this,
                            500,
                            openIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    startActivityAndCollapse(pendingIntent);
                } else {
                    startActivityAndCollapse(openIntent);
                }
            } catch (Exception ignored) {
                startActivity(openIntent);
            }
            updateTile();
            return;
        }

        boolean enabled = SettingsStore.prefs(this).getBoolean(SettingsStore.KEY_GLOBAL_MASK, false);
        SettingsStore.prefs(this).edit()
                .putBoolean(SettingsStore.KEY_GLOBAL_MASK, !enabled)
                .putLong(SettingsStore.KEY_SNOOZE_UNTIL, 0)
                .apply();
        if (!enabled) {
            ensureOverlayServiceRunning();
        }
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean consent = SettingsStore.prefs(this).getBoolean(SettingsStore.KEY_CONSENT_GRANTED, false);
        boolean enabled = consent && SettingsStore.prefs(this).getBoolean(SettingsStore.KEY_GLOBAL_MASK, false);
        tile.setLabel("防窺");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(!consent ? "需同意" : (enabled ? "已開啟" : "已關閉"));
        }
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    private void ensureOverlayServiceRunning() {
        Intent intent = new Intent(this, PrivacyOverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception ignored) {
        }
    }
}
