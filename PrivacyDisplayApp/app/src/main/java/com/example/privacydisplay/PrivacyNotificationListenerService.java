package com.example.privacydisplay;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;

public class PrivacyNotificationListenerService extends NotificationListenerService {
    private static final String CHANNEL_ID = "privacy_display_masked_notifications";
    private static final long TEMP_OVERLAY_MS = 5000L;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;

        SharedPreferences prefs = SettingsStore.prefs(this);
        if (!prefs.getBoolean(SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, true)) return;

        Set<String> selected = SettingsStore.selectedPackages(this);
        if (!selected.contains(sbn.getPackageName())) return;

        prefs.edit()
                .putLong(SettingsStore.KEY_TEMP_OVERLAY_UNTIL, System.currentTimeMillis() + TEMP_OVERLAY_MS)
                .apply();
        ensureOverlayServiceRunning();

        boolean cancelOriginal = prefs.getBoolean(SettingsStore.KEY_CANCEL_NOTIFICATIONS, false);
        if (cancelOriginal && sbn.isClearable()) {
            try {
                cancelNotification(sbn.getKey());
            } catch (Exception ignored) {
            }
            sendGenericHiddenNotification(sbn.getPackageName());
        }
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

    private void sendGenericHiddenNotification(String packageName) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        createChannel();
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                300,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_privacy)
                .setContentTitle("已隱藏一則通知")
                .setContentText(getAppLabel(packageName) + " 的通知已由 Privacy Display 處理")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            int id = (int) (System.currentTimeMillis() & 0x0fffffff);
            manager.notify(id, notification);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Privacy Display 隱藏通知",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("以通用文字取代敏感通知內容");
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private String getAppLabel(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }
}
