package com.example.privacydisplay;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private TextView statusText;
    private TextView opacityText;
    private LinearLayout appListContainer;
    private Switch globalSwitch;
    private Switch appMaskSwitch;
    private Switch notificationMaskSwitch;
    private Switch cancelNotificationSwitch;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (sharedPreferences, key) -> refreshSwitchesOnly();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsStore.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener);
        buildUi();
        requestPostNotificationsIfNeeded();
        startOverlayService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        loadAppList();
        refreshSwitchesOnly();
    }

    @Override
    protected void onDestroy() {
        SettingsStore.prefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Privacy Display 智慧防窺", 26, true);
        title.setTextColor(Color.rgb(20, 20, 20));
        root.addView(title);

        TextView subtitle = text("用懸浮遮罩保護畫面，可設定全螢幕、指定 App、指定 App 通知觸發。", 15, false);
        subtitle.setTextColor(Color.rgb(80, 80, 80));
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        statusText = text("", 14, false);
        statusText.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusText.setBackgroundColor(Color.rgb(245, 245, 245));
        root.addView(statusText, fullWidth());

        root.addView(sectionTitle("必要權限"));
        root.addView(button("1. 開啟『顯示在其他 App 上層』", v -> openOverlayPermission()));
        root.addView(button("2. 開啟『使用情況存取權』", v -> openUsageAccessSettings()));
        root.addView(button("3. 開啟『通知存取權』", v -> openNotificationAccessSettings()));
        root.addView(button("4. 允許此 App 發送通用通知", v -> requestPostNotificationsIfNeeded()));
        root.addView(button("啟動 / 重新啟動防窺服務", v -> startOverlayService()));

        root.addView(sectionTitle("防窺模式"));
        globalSwitch = makeSwitch("隱藏整個手機畫面（全螢幕遮罩）", SettingsStore.KEY_GLOBAL_MASK, false);
        root.addView(globalSwitch);
        appMaskSwitch = makeSwitch("只在指定 App 開啟時自動遮罩", SettingsStore.KEY_APP_MASK_ENABLED, true);
        root.addView(appMaskSwitch);
        notificationMaskSwitch = makeSwitch("收到指定 App 通知時短暫遮罩", SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, true);
        root.addView(notificationMaskSwitch);
        cancelNotificationSwitch = makeSwitch("通知防窺：取消原通知，改發『已隱藏一則通知』", SettingsStore.KEY_CANCEL_NOTIFICATIONS, false);
        root.addView(cancelNotificationSwitch);

        root.addView(sectionTitle("遮罩深度"));
        opacityText = text("", 14, false);
        root.addView(opacityText);
        SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(255);
        opacitySeek.setProgress(SettingsStore.overlayOpacity(this));
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = Math.max(40, progress);
                SettingsStore.setOverlayOpacity(MainActivity.this, value);
                updateOpacityLabel(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        updateOpacityLabel(SettingsStore.overlayOpacity(this));
        root.addView(opacitySeek, fullWidth());

        root.addView(sectionTitle("選擇要保護的 App"));
        TextView appHint = text("勾選後，這些 App 在前景時會自動遮罩；若這些 App 發通知，也可觸發通知防窺。", 14, false);
        appHint.setTextColor(Color.rgb(90, 90, 90));
        root.addView(appHint);
        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        appListContainer.setPadding(0, dp(8), 0, dp(8));
        root.addView(appListContainer, fullWidth());

        root.addView(button("全部取消勾選", v -> clearSelectedApps()));
        root.addView(button("停止服務並關閉所有防窺開關", v -> stopEverything()));

        TextView footer = text("提示：防窺層不會吃掉觸控；輸入密碼時仍會持續保護畫面。需要暫停時可用通知按鈕或『防窺』快速設定磚。", 13, false);
        footer.setTextColor(Color.rgb(100, 100, 100));
        footer.setPadding(0, dp(16), 0, 0);
        root.addView(footer);

        setContentView(scrollView);
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, true);
        view.setPadding(0, dp(22), 0, dp(8));
        return view;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setLineSpacing(0, 1.15f);
        if (bold) textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return textView;
    }

    private Button button(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setOnClickListener(listener);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(48));
        return button;
    }

    private Switch makeSwitch(String label, String key, boolean defaultValue) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(15);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(SettingsStore.prefs(this).getBoolean(key, defaultValue));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsStore.prefs(this).edit().putBoolean(key, isChecked).apply();
            startOverlayService();
        });
        return sw;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateOpacityLabel(int value) {
        int percent = Math.round((value / 255f) * 100f);
        if (opacityText != null) opacityText.setText("目前遮罩不透明度：約 " + percent + "%");
    }

    private void refreshStatus() {
        boolean overlay = hasOverlayPermission();
        boolean usage = hasUsageAccess();
        boolean notification = hasNotificationListenerAccess();
        boolean postNotifications = hasPostNotificationsPermission();
        String status = "權限狀態\n"
                + (overlay ? "✅" : "❌") + " 顯示在其他 App 上層\n"
                + (usage ? "✅" : "❌") + " 使用情況存取權（判斷目前是哪個 App）\n"
                + (notification ? "✅" : "❌") + " 通知存取權（通知防窺）\n"
                + (postNotifications ? "✅" : "❌") + " 發送通用通知權限";
        statusText.setText(status);
    }

    private void refreshSwitchesOnly() {
        SharedPreferences prefs = SettingsStore.prefs(this);
        setSwitchWithoutLoop(globalSwitch, prefs.getBoolean(SettingsStore.KEY_GLOBAL_MASK, false));
        setSwitchWithoutLoop(appMaskSwitch, prefs.getBoolean(SettingsStore.KEY_APP_MASK_ENABLED, true));
        setSwitchWithoutLoop(notificationMaskSwitch, prefs.getBoolean(SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, true));
        setSwitchWithoutLoop(cancelNotificationSwitch, prefs.getBoolean(SettingsStore.KEY_CANCEL_NOTIFICATIONS, false));
    }

    private void setSwitchWithoutLoop(Switch sw, boolean value) {
        if (sw == null || sw.isChecked() == value) return;
        CompoundButton.OnCheckedChangeListener old = null;
        sw.setOnCheckedChangeListener(old);
        sw.setChecked(value);
        String key;
        if (sw == globalSwitch) key = SettingsStore.KEY_GLOBAL_MASK;
        else if (sw == appMaskSwitch) key = SettingsStore.KEY_APP_MASK_ENABLED;
        else if (sw == notificationMaskSwitch) key = SettingsStore.KEY_NOTIFICATION_MASK_ENABLED;
        else key = SettingsStore.KEY_CANCEL_NOTIFICATIONS;
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsStore.prefs(this).edit().putBoolean(key, isChecked).apply();
            startOverlayService();
        });
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasNotificationListenerAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName componentName = new ComponentName(this, PrivacyNotificationListenerService.class);
        String flat = componentName.flattenToString();
        return enabled.contains(flat) || enabled.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));
    }

    private boolean hasPostNotificationsPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 813);
        }
    }

    private void openOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openUsageAccessSettings() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private void openNotificationAccessSettings() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, PrivacyOverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "無法啟動服務：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopEverything() {
        SettingsStore.prefs(this).edit()
                .putBoolean(SettingsStore.KEY_GLOBAL_MASK, false)
                .putBoolean(SettingsStore.KEY_APP_MASK_ENABLED, false)
                .putBoolean(SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, false)
                .putBoolean(SettingsStore.KEY_CANCEL_NOTIFICATIONS, false)
                .putLong(SettingsStore.KEY_TEMP_OVERLAY_UNTIL, 0)
                .apply();
        stopService(new Intent(this, PrivacyOverlayService.class));
        refreshSwitchesOnly();
        Toast.makeText(this, "已停止 Privacy Display", Toast.LENGTH_SHORT).show();
    }

    private void clearSelectedApps() {
        SettingsStore.prefs(this).edit().putStringSet(SettingsStore.KEY_SELECTED_PACKAGES, new HashSet<String>()).apply();
        loadAppList();
    }

    private void loadAppList() {
        if (appListContainer == null) return;
        appListContainer.removeAllViews();
        List<AppEntry> apps = launcherApps();
        Set<String> selected = SettingsStore.selectedPackages(this);
        if (apps.isEmpty()) {
            appListContainer.addView(text("找不到可選擇的 App。", 14, false));
            return;
        }
        for (AppEntry app : apps) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(app.label + "\n" + app.packageName);
            checkBox.setTextSize(14);
            checkBox.setPadding(0, dp(8), 0, dp(8));
            checkBox.setChecked(selected.contains(app.packageName));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> SettingsStore.setPackageSelected(this, app.packageName, isChecked));
            appListContainer.addView(checkBox, fullWidth());
        }
    }

    private List<AppEntry> launcherApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        Map<String, AppEntry> unique = new HashMap<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            CharSequence label = info.loadLabel(pm);
            unique.put(pkg, new AppEntry(pkg, label == null ? pkg : label.toString()));
        }
        List<AppEntry> result = new ArrayList<>(unique.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        result.sort((a, b) -> collator.compare(a.label, b.label));
        return result;
    }

    private static class AppEntry {
        final String packageName;
        final String label;
        AppEntry(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }
}
