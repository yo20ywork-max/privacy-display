package tw.privacylab.privacydisplay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
    private static final int REQUEST_POST_NOTIFICATIONS = 813;

    private static final String DISCLOSURE_MESSAGE =
            "Privacy Display 是純 App 智慧防窺工具，會使用 Android 允許的權限提供軟體遮罩與側視干擾濾鏡。\n\n" +
            "1. 顯示在其他 App 上層：用半透明遮罩或側視干擾濾鏡覆蓋畫面。\n" +
            "2. 使用情況存取權：只在本機判斷目前前景 App 是否在你選擇的清單內。\n" +
            "3. 通知存取權：只在本機判斷指定 App 是否送出通知，並依設定短暫啟用遮罩或改以一般提醒顯示。\n\n" +
            "純 App 無法改變螢幕硬體可視角度，也不能保證所有角度與設備都絕對防窺。本 App 會用軟體遮罩、百葉紋、亮度壓低與微干擾降低旁看可讀性；若要完全遮住內容，請關閉側視干擾濾鏡並提高遮罩強度。";

    private TextView statusText;
    private TextView opacityText;
    private LinearLayout appListContainer;
    private Switch globalSwitch;
    private Switch appMaskSwitch;
    private Switch notificationMaskSwitch;
    private Switch cancelNotificationSwitch;
    private Switch readableFilterSwitch;

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (sharedPreferences, key) -> refreshSwitchesOnly();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsStore.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener);
        buildUi();
        if (!SettingsStore.disclosureAccepted(this)) {
            showProminentDisclosureDialog(false);
        } else if (isAnyProtectionEnabled()) {
            startOverlayService();
        }
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
        title.setTextColor(Color.rgb(18, 18, 18));
        root.addView(title);

        TextView subtitle = text("純 App 防窺：全螢幕遮罩、指定 App / 通知觸發，並提供正面仍可閱讀的側視干擾濾鏡。", 15, false);
        subtitle.setTextColor(Color.rgb(78, 78, 78));
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        statusText = cardText("");
        root.addView(statusText, fullWidth());

        root.addView(sectionTitle("權限與揭露"));
        TextView disclosure = cardText(DISCLOSURE_MESSAGE);
        disclosure.setTextColor(Color.rgb(55, 55, 55));
        root.addView(disclosure, fullWidth());
        root.addView(button("查看 / 重新同意權限揭露", v -> showProminentDisclosureDialog(true)), fullWidth());
        root.addView(button("查看隱私權政策摘要", v -> showPrivacyPolicyDialog()), fullWidth());

        root.addView(sectionTitle("設定權限"));
        root.addView(button("1. 開啟上層顯示權限", v -> { if (requireDisclosureAccepted()) openOverlayPermission(); }), fullWidth());
        root.addView(button("2. 開啟使用情況存取權", v -> { if (requireDisclosureAccepted()) openUsageAccessSettings(); }), fullWidth());
        root.addView(button("3. 開啟通知存取權", v -> { if (requireDisclosureAccepted()) openNotificationAccessSettings(); }), fullWidth());
        root.addView(button("4. 允許 Privacy Display 發送提醒", v -> { if (requireDisclosureAccepted()) requestPostNotificationsIfNeeded(); }), fullWidth());
        root.addView(button("啟動 / 重新整理防窺服務", v -> { if (requireDisclosureAccepted()) startOverlayService(); }), fullWidth());

        root.addView(sectionTitle("防窺模式"));
        globalSwitch = makeSwitch("隱藏整個手機畫面", SettingsStore.KEY_GLOBAL_MASK, false);
        root.addView(globalSwitch, fullWidth());
        appMaskSwitch = makeSwitch("只在指定 App 位於前景時啟用防窺", SettingsStore.KEY_APP_MASK_ENABLED, false);
        root.addView(appMaskSwitch, fullWidth());
        notificationMaskSwitch = makeSwitch("指定 App 收到通知時短暫啟用防窺", SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, false);
        root.addView(notificationMaskSwitch, fullWidth());
        cancelNotificationSwitch = makeSwitch("通知防窺時移除原通知，改顯示一般提醒", SettingsStore.KEY_CANCEL_NOTIFICATIONS, false);
        root.addView(cancelNotificationSwitch, fullWidth());
        readableFilterSwitch = makeSwitch("純 App 側視干擾濾鏡（正面仍可閱讀）", SettingsStore.KEY_READABLE_FILTER_ENABLED, true);
        root.addView(readableFilterSwitch, fullWidth());

        TextView filterNote = text("開啟時會以百葉紋、斜向干擾線與壓暗處理降低旁看可讀性；關閉時改為傳統黑幕遮罩，遮擋更強但正面觀看也會受影響。", 13, false);
        filterNote.setTextColor(Color.rgb(96, 96, 96));
        root.addView(filterNote, fullWidth());

        root.addView(sectionTitle("圖片防窺 Pro"));
        TextView imageProHint = text("將 HEIC、PNG、JPG、WebP、GIF、BMP、AVIF 等 Android 可讀取的圖片轉成自帶防窺視覺效果的影像檔。", 14, false);
        imageProHint.setTextColor(Color.rgb(78, 78, 78));
        root.addView(imageProHint, fullWidth());
        root.addView(button("開啟圖片防窺轉檔 Pro", v -> startActivity(new Intent(this, ImagePrivacyActivity.class))), fullWidth());

        root.addView(sectionTitle("遮罩強度"));
        opacityText = text("", 14, false);
        root.addView(opacityText, fullWidth());
        SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(255);
        opacitySeek.setProgress(SettingsStore.overlayOpacity(this));
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = Math.max(40, progress);
                SettingsStore.setOverlayOpacity(MainActivity.this, value);
                updateOpacityLabel(value);
                if (isAnyProtectionEnabled()) startOverlayService();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        updateOpacityLabel(SettingsStore.overlayOpacity(this));
        root.addView(opacitySeek, fullWidth());

        root.addView(sectionTitle("選擇要保護的 App"));
        TextView appHint = text("勾選後，指定 App 防窺與通知防窺都會使用這份清單。", 14, false);
        appHint.setTextColor(Color.rgb(90, 90, 90));
        root.addView(appHint, fullWidth());
        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        appListContainer.setPadding(0, dp(8), 0, dp(8));
        root.addView(appListContainer, fullWidth());

        root.addView(button("清除已選 App", v -> clearSelectedApps()), fullWidth());
        root.addView(button("停止所有防窺功能", v -> stopEverything()), fullWidth());

        TextView footer = text("提示：防窺層不會吃掉觸控；輸入密碼時仍會持續保護畫面。需要暫停時可用通知按鈕或快速設定 Tile。", 13, false);
        footer.setTextColor(Color.rgb(96, 96, 96));
        footer.setPadding(0, dp(16), 0, 0);
        root.addView(footer, fullWidth());

        setContentView(scrollView);
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, true);
        view.setPadding(0, dp(22), 0, dp(8));
        return view;
    }

    private TextView cardText(String value) {
        TextView view = text(value, 14, false);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackgroundColor(Color.rgb(245, 245, 245));
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
        attachSwitchListener(sw, key);
        return sw;
    }

    private void attachSwitchListener(Switch sw, String key) {
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !requireDisclosureAccepted()) {
                buttonView.setChecked(false);
                return;
            }
            SettingsStore.prefs(this).edit().putBoolean(key, isChecked).apply();
            if (SettingsStore.disclosureAccepted(this) && isAnyProtectionEnabled()) {
                startOverlayService();
            } else if (!isAnyProtectionEnabled()) {
                stopService(new Intent(this, PrivacyOverlayService.class));
            }
        });
    }

    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateOpacityLabel(int value) {
        int percent = Math.round((value / 255f) * 100f);
        if (opacityText != null) opacityText.setText("目前防窺強度：" + percent + "%");
    }

    private void refreshStatus() {
        boolean disclosure = SettingsStore.disclosureAccepted(this);
        boolean overlay = hasOverlayPermission();
        boolean usage = hasUsageAccess();
        boolean notification = hasNotificationListenerAccess();
        boolean postNotifications = hasPostNotificationsPermission();
        String status = "狀態\n"
                + statusLine(disclosure, "已同意權限揭露")
                + statusLine(overlay, "可顯示上層防窺濾鏡")
                + statusLine(usage, "可判斷目前前景 App")
                + statusLine(notification, "可監聽指定 App 通知")
                + statusLine(postNotifications, "可發送替代提醒")
                + statusLine(SettingsStore.readableFilterEnabled(this), "側視干擾濾鏡已開啟")
                + statusLine(SettingsStore.isImagePrivacyUnlocked(this), "圖片防窺 Pro 已解鎖");
        statusText.setText(status);
    }

    private String statusLine(boolean ok, String label) {
        return (ok ? "✓ " : "• ") + label + "\n";
    }

    private void refreshSwitchesOnly() {
        SharedPreferences prefs = SettingsStore.prefs(this);
        setSwitchWithoutLoop(globalSwitch, SettingsStore.KEY_GLOBAL_MASK, prefs.getBoolean(SettingsStore.KEY_GLOBAL_MASK, false));
        setSwitchWithoutLoop(appMaskSwitch, SettingsStore.KEY_APP_MASK_ENABLED, prefs.getBoolean(SettingsStore.KEY_APP_MASK_ENABLED, false));
        setSwitchWithoutLoop(notificationMaskSwitch, SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, prefs.getBoolean(SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, false));
        setSwitchWithoutLoop(cancelNotificationSwitch, SettingsStore.KEY_CANCEL_NOTIFICATIONS, prefs.getBoolean(SettingsStore.KEY_CANCEL_NOTIFICATIONS, false));
        setSwitchWithoutLoop(readableFilterSwitch, SettingsStore.KEY_READABLE_FILTER_ENABLED, prefs.getBoolean(SettingsStore.KEY_READABLE_FILTER_ENABLED, true));
    }

    private void setSwitchWithoutLoop(Switch sw, String key, boolean value) {
        if (sw == null || sw.isChecked() == value) return;
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(value);
        attachSwitchListener(sw, key);
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
        return enabled.contains(flat)
                || enabled.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));
    }

    private boolean hasPostNotificationsPermission() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        } else {
            Toast.makeText(this, "提醒權限已可使用。", Toast.LENGTH_SHORT).show();
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
        if (!SettingsStore.disclosureAccepted(this)) return;
        Intent intent = new Intent(this, PrivacyOverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            refreshStatus();
        } catch (Exception e) {
            Toast.makeText(this, "無法啟動防窺服務：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopEverything() {
        SettingsStore.prefs(this).edit()
                .putBoolean(SettingsStore.KEY_GLOBAL_MASK, false)
                .putBoolean(SettingsStore.KEY_APP_MASK_ENABLED, false)
                .putBoolean(SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, false)
                .putBoolean(SettingsStore.KEY_CANCEL_NOTIFICATIONS, false)
                .putLong(SettingsStore.KEY_TEMP_OVERLAY_UNTIL, 0)
                .putLong(SettingsStore.KEY_SNOOZE_UNTIL, 0)
                .apply();
        stopService(new Intent(this, PrivacyOverlayService.class));
        refreshSwitchesOnly();
        refreshStatus();
        Toast.makeText(this, "已停止 Privacy Display。", Toast.LENGTH_SHORT).show();
    }

    private void clearSelectedApps() {
        SettingsStore.prefs(this).edit()
                .putStringSet(SettingsStore.KEY_SELECTED_PACKAGES, new HashSet<String>())
                .apply();
        loadAppList();
    }

    private void loadAppList() {
        if (appListContainer == null) return;
        appListContainer.removeAllViews();
        if (!SettingsStore.disclosureAccepted(this)) {
            TextView message = text("請先同意權限揭露，才會載入可選 App 清單。", 14, false);
            message.setTextColor(Color.rgb(110, 110, 110));
            appListContainer.addView(message, fullWidth());
            return;
        }
        List<AppEntry> apps = launcherApps();
        Set<String> selected = SettingsStore.selectedPackages(this);
        if (apps.isEmpty()) {
            appListContainer.addView(text("找不到可選的 App。", 14, false), fullWidth());
            return;
        }
        for (AppEntry app : apps) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(app.label + "\n" + app.packageName);
            checkBox.setTextSize(14);
            checkBox.setPadding(0, dp(8), 0, dp(8));
            checkBox.setChecked(selected.contains(app.packageName));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                    SettingsStore.setPackageSelected(this, app.packageName, isChecked));
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

    private boolean isAnyProtectionEnabled() {
        SharedPreferences prefs = SettingsStore.prefs(this);
        return prefs.getBoolean(SettingsStore.KEY_GLOBAL_MASK, false)
                || prefs.getBoolean(SettingsStore.KEY_APP_MASK_ENABLED, false)
                || prefs.getBoolean(SettingsStore.KEY_NOTIFICATION_MASK_ENABLED, false);
    }

    private boolean requireDisclosureAccepted() {
        if (SettingsStore.disclosureAccepted(this)) return true;
        showProminentDisclosureDialog(false);
        Toast.makeText(this, "請先閱讀並同意權限揭露。", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void showProminentDisclosureDialog(boolean optional) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Privacy Display 權限揭露")
                .setMessage(DISCLOSURE_MESSAGE)
                .setPositiveButton("同意並繼續", (d, which) -> {
                    SettingsStore.setDisclosureAccepted(MainActivity.this, true);
                    refreshStatus();
                    loadAppList();
                    Toast.makeText(MainActivity.this, "已啟用設定。", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(optional ? "關閉" : "不同意", (d, which) -> {
                    if (!optional) stopEverything();
                })
                .create();
        dialog.setCanceledOnTouchOutside(optional);
        dialog.setCancelable(optional);
        dialog.show();
    }

    private void showPrivacyPolicyDialog() {
        String message = "Privacy Display 的防窺設定、App 清單、通知判斷與圖片處理都保存在本機。\n\n"
                + "本 App 不使用 INTERNET 權限，不會把你的圖片、通知內容、使用紀錄或選取的 App 清單傳到伺服器。\n\n"
                + "純 App 防窺是軟體遮罩與視覺干擾，不會改變螢幕硬體可視角度。";
        new AlertDialog.Builder(this)
                .setTitle("隱私權政策摘要")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
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
