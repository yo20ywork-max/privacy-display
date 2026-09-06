package com.example.privacydisplay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class SettingsStore {
    private SettingsStore() {}

    public static final String PREFS = "privacy_display_prefs";

    public static final String KEY_GLOBAL_MASK = "global_mask";
    public static final String KEY_APP_MASK_ENABLED = "app_mask_enabled";
    public static final String KEY_NOTIFICATION_MASK_ENABLED = "notification_mask_enabled";
    public static final String KEY_CANCEL_NOTIFICATIONS = "cancel_notifications";
    public static final String KEY_SELECTED_PACKAGES = "selected_packages";
    public static final String KEY_OPACITY = "overlay_opacity";
    public static final String KEY_TEMP_OVERLAY_UNTIL = "temp_overlay_until";
    public static final String KEY_SNOOZE_UNTIL = "snooze_until";

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> selectedPackages(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_SELECTED_PACKAGES, new HashSet<String>()));
    }

    public static void setPackageSelected(Context context, String packageName, boolean selected) {
        Set<String> current = selectedPackages(context);
        if (selected) {
            current.add(packageName);
        } else {
            current.remove(packageName);
        }
        prefs(context).edit().putStringSet(KEY_SELECTED_PACKAGES, current).apply();
    }

    public static int overlayOpacity(Context context) {
        return prefs(context).getInt(KEY_OPACITY, 225);
    }

    public static void setOverlayOpacity(Context context, int opacity) {
        int safe = Math.max(40, Math.min(255, opacity));
        prefs(context).edit().putInt(KEY_OPACITY, safe).apply();
    }
}
