package tw.privacylab.privacydisplay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class SettingsStore {
    private SettingsStore() {}

    public static final String PREFS = "privacy_display_prefs";

    public static final String KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted";
    public static final String KEY_CONSENT_GRANTED = KEY_DISCLOSURE_ACCEPTED;
    public static final String KEY_GLOBAL_MASK = "global_mask";
    public static final String KEY_APP_MASK_ENABLED = "app_mask_enabled";
    public static final String KEY_NOTIFICATION_MASK_ENABLED = "notification_mask_enabled";
    public static final String KEY_CANCEL_NOTIFICATIONS = "cancel_notifications";
    public static final String KEY_READABLE_FILTER_ENABLED = "readable_filter_enabled";
    public static final String KEY_SELECTED_PACKAGES = "selected_packages";
    public static final String KEY_OPACITY = "overlay_opacity";
    public static final String KEY_TEMP_OVERLAY_UNTIL = "temp_overlay_until";
    public static final String KEY_SNOOZE_UNTIL = "snooze_until";
    public static final String KEY_IMAGE_PRIVACY_UNLOCKED = "image_privacy_unlocked";

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean disclosureAccepted(Context context) {
        return prefs(context).getBoolean(KEY_DISCLOSURE_ACCEPTED, false);
    }

    public static void setDisclosureAccepted(Context context, boolean accepted) {
        prefs(context).edit().putBoolean(KEY_DISCLOSURE_ACCEPTED, accepted).apply();
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

    public static boolean isImagePrivacyUnlocked(Context context) {
        return prefs(context).getBoolean(KEY_IMAGE_PRIVACY_UNLOCKED, false);
    }

    public static void setImagePrivacyUnlocked(Context context, boolean unlocked) {
        prefs(context).edit().putBoolean(KEY_IMAGE_PRIVACY_UNLOCKED, unlocked).apply();
    }

    public static int overlayOpacity(Context context) {
        return prefs(context).getInt(KEY_OPACITY, 225);
    }

    public static void setOverlayOpacity(Context context, int opacity) {
        int safe = Math.max(40, Math.min(255, opacity));
        prefs(context).edit().putInt(KEY_OPACITY, safe).apply();
    }

    public static boolean readableFilterEnabled(Context context) {
        return prefs(context).getBoolean(KEY_READABLE_FILTER_ENABLED, true);
    }
}
