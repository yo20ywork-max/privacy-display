package tw.privacylab.privacydisplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Set;

public class PrivacyOverlayService extends Service {
    public static final String ACTION_DISABLE_MASK = "tw.privacylab.privacydisplay.DISABLE_MASK";

    private static final String CHANNEL_ID = "privacy_display_running";
    private static final int RUNNING_NOTIFICATION_ID = 2701;
    private static final long POLL_MS = 700L;
    private static final long SNOOZE_MS = 8000L;
    private static final float FALLBACK_MAX_TOUCH_OBSCURING_ALPHA = 0.8f;
    private static final float TOUCH_PASSTHROUGH_ALPHA_MARGIN = 0.01f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlayView;
    private PrivacyFilterView filterView;
    private TextView overlayLabel;
    private String lastForegroundPackage;

    private final Runnable monitorRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                monitorOnce();
            } finally {
                handler.postDelayed(this, POLL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createRunningChannel();
        startAsForeground();
        handler.post(monitorRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISABLE_MASK.equals(intent.getAction())) {
            SettingsStore.prefs(this).edit()
                    .putBoolean(SettingsStore.KEY_GLOBAL_MASK, false)
                    .putLong(SettingsStore.KEY_TEMP_OVERLAY_UNTIL, 0)
                    .putLong(SettingsStore.KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
                    .apply();
            hideOverlay();
        }
        startAsForeground();
        monitorOnce();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void monitorOnce() {
        SharedPreferences prefs = SettingsStore.prefs(this);
        if (!prefs.getBoolean(SettingsStore.KEY_CONSENT_GRANTED, false)) {
            hideOverlay();
            return;
        }

        long now = System.currentTimeMillis();
        long snoozeUntil = prefs.getLong(SettingsStore.KEY_SNOOZE_UNTIL, 0);
        if (now < snoozeUntil) {
            hideOverlay();
            return;
        }

        boolean shouldShow = prefs.getBoolean(SettingsStore.KEY_GLOBAL_MASK, false);

        long tempUntil = prefs.getLong(SettingsStore.KEY_TEMP_OVERLAY_UNTIL, 0);
        if (now < tempUntil) {
            shouldShow = true;
        }

        if (!shouldShow && prefs.getBoolean(SettingsStore.KEY_APP_MASK_ENABLED, false)) {
            String foreground = getForegroundPackage();
            if (foreground != null) lastForegroundPackage = foreground;
            Set<String> selected = SettingsStore.selectedPackages(this);
            shouldShow = lastForegroundPackage != null && selected.contains(lastForegroundPackage);
        }

        if (shouldShow) {
            showOverlay();
        } else {
            hideOverlay();
        }
    }

    private String getForegroundPackage() {
        UsageStatsManager usageStatsManager =
                (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return null;

        long end = System.currentTimeMillis();
        long begin = end - 5000L;
        UsageEvents usageEvents;
        try {
            usageEvents = usageStatsManager.queryEvents(begin, end);
        } catch (SecurityException securityException) {
            return null;
        }

        UsageEvents.Event event = new UsageEvents.Event();
        String candidate = null;
        long latest = 0L;
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            int type = event.getEventType();
            boolean isForegroundEvent = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isForegroundEvent = isForegroundEvent || type == UsageEvents.Event.ACTIVITY_RESUMED;
            }
            if (isForegroundEvent && event.getTimeStamp() >= latest) {
                candidate = event.getPackageName();
                latest = event.getTimeStamp();
            }
        }
        return candidate;
    }

    private void showOverlay() {
        if (!canDrawOverlay()) return;
        int opacity = SettingsStore.overlayOpacity(this);
        boolean readableFilter = SettingsStore.readableFilterEnabled(this);

        if (overlayView != null) {
            if (filterView != null) filterView.setConfig(opacity, readableFilter);
            updateOverlayLabel(readableFilter);
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setFocusable(false);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        filterView = new PrivacyFilterView(this);
        filterView.setConfig(opacity, readableFilter);
        root.addView(filterView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        overlayLabel = new TextView(this);
        overlayLabel.setTextColor(Color.WHITE);
        overlayLabel.setGravity(Gravity.CENTER);
        overlayLabel.setLineSpacing(0, 1.2f);
        updateOverlayLabel(readableFilter);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                readableFilter ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.CENTER
        );
        int pad = dp(18);
        labelParams.setMargins(pad, pad, pad, pad + dp(18));
        root.addView(overlayLabel, labelParams);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.alpha = touchPassthroughWindowAlpha();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        try {
            windowManager.addView(root, params);
            overlayView = root;
        } catch (Exception ignored) {
            overlayView = null;
            filterView = null;
            overlayLabel = null;
        }
    }

    private void updateOverlayLabel(boolean readableFilter) {
        if (overlayLabel == null) return;
        if (readableFilter) {
            overlayLabel.setText("Privacy Display 側視干擾濾鏡中 · 可直接操作手機");
            overlayLabel.setTextSize(13);
            overlayLabel.setAlpha(0.72f);
            overlayLabel.setBackgroundColor(Color.argb(92, 0, 0, 0));
            overlayLabel.setPadding(dp(10), dp(8), dp(10), dp(8));
        } else {
            overlayLabel.setText("Privacy Display\n防窺遮罩已啟用\n可直接操作手機\n從通知或快速設定暫停");
            overlayLabel.setTextSize(23);
            overlayLabel.setAlpha(1f);
            overlayLabel.setBackgroundColor(Color.TRANSPARENT);
            overlayLabel.setPadding(0, 0, 0, 0);
        }
    }

    private void hideOverlay() {
        if (overlayView == null || windowManager == null) return;
        try {
            windowManager.removeView(overlayView);
        } catch (Exception ignored) {
        } finally {
            overlayView = null;
            filterView = null;
            overlayLabel = null;
        }
    }

    private boolean canDrawOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private float touchPassthroughWindowAlpha() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return 1f;
        }

        float maxAlpha = FALLBACK_MAX_TOUCH_OBSCURING_ALPHA;
        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (inputManager != null) {
            maxAlpha = inputManager.getMaximumObscuringOpacityForTouch();
        }
        maxAlpha = Math.max(0f, Math.min(1f, maxAlpha));
        return Math.max(0f, maxAlpha - TOUCH_PASSTHROUGH_ALPHA_MARGIN);
    }

    private void createRunningChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Privacy Display 執行中",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("顯示防窺遮罩服務正在執行。");
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void startAsForeground() {
        Notification notification = buildRunningNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        RUNNING_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(RUNNING_NOTIFICATION_ID, notification);
            }
        } catch (Exception ignored) {
            startForeground(RUNNING_NOTIFICATION_ID, notification);
        }
    }

    private Notification buildRunningNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent disableIntent = new Intent(this, PrivacyOverlayService.class);
        disableIntent.setAction(ACTION_DISABLE_MASK);
        PendingIntent disablePendingIntent = PendingIntent.getService(
                this,
                101,
                disableIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_privacy)
                .setContentTitle("Privacy Display 執行中")
                .setContentText("正在依你的設定啟用純 App 防窺濾鏡。")
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(R.drawable.ic_privacy, "暫停防窺", disablePendingIntent)
                .build();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class PrivacyFilterView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int opacity = 225;
        private boolean readableFilter = true;

        PrivacyFilterView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        void setConfig(int opacity, boolean readableFilter) {
            this.opacity = Math.max(40, Math.min(255, opacity));
            this.readableFilter = readableFilter;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            if (!readableFilter) {
                canvas.drawColor(Color.argb(opacity, 0, 0, 0));
                return;
            }

            float strength = opacity / 255f;
            canvas.drawColor(Color.argb(Math.round(54 + 74 * strength), 0, 0, 0));

            int pitch = Math.max(6, Math.round(width / 72f));
            int stripeWidth = Math.max(2, Math.round(pitch * 0.46f));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(70 + 110 * strength), 0, 0, 0));
            for (int x = -pitch; x < width + pitch; x += pitch) {
                canvas.drawRect(x, 0, x + stripeWidth, height, paint);
            }

            paint.setStrokeWidth(Math.max(1f, pitch / 7f));
            paint.setColor(Color.argb(Math.round(34 + 58 * strength), 255, 255, 255));
            for (int x = -height; x < width + height; x += pitch * 3) {
                canvas.drawLine(x, 0, x + height, height, paint);
            }

            paint.setColor(Color.argb(Math.round(28 + 52 * strength), 0, 0, 0));
            for (int y = 0; y < height; y += Math.max(5, pitch * 2)) {
                canvas.drawRect(0, y, width, y + 1, paint);
            }

            float edgeWidth = width * 0.18f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(48 + 72 * strength), 0, 0, 0));
            canvas.drawRect(0, 0, edgeWidth, height, paint);
            canvas.drawRect(width - edgeWidth, 0, width, height, paint);
        }
    }
}
