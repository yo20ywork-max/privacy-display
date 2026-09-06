package tw.privacylab.privacydisplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.heifwriter.HeifWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Paid local image converter. It creates a regular image file with a privacy-display visual
 * treatment; it cannot embed a hardware privacy filter into the file itself.
 */
public class ImagePrivacyActivity extends Activity implements BillingManager.Listener {
    private static final int REQUEST_PICK_IMAGE = 4201;
    private static final int REQUEST_CREATE_OUTPUT = 4202;
    private static final int MAX_DECODE_DIMENSION = 4096;

    private BillingManager billingManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView unlockStatusText;
    private TextView productText;
    private TextView processingStatusText;
    private TextView selectedImageText;
    private TextView strengthText;
    private TextView stripeText;
    private Button buyButton;
    private Button restoreButton;
    private Button pickButton;
    private Button convertButton;
    private Button saveButton;
    private ProgressBar progressBar;
    private ImageView previewImage;
    private SeekBar strengthSeek;
    private SeekBar stripeSeek;
    private CheckBox keepColorCheck;
    private CheckBox watermarkCheck;
    private Spinner formatSpinner;

    private Bitmap sourceBitmap;
    private Bitmap convertedBitmap;
    private OutputFormat pendingOutputFormat = OutputFormat.PNG;

    enum OutputFormat {
        PNG("PNG", "image/png", ".png"),
        JPEG("JPG / JPEG", "image/jpeg", ".jpg"),
        WEBP("WebP", "image/webp", ".webp"),
        HEIC("HEIC / HEIF", "image/heic", ".heic");

        final String label;
        final String mimeType;
        final String extension;

        OutputFormat(String label, String mimeType, String extension) {
            this.label = label;
            this.mimeType = mimeType;
            this.extension = extension;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        billingManager = BillingManager.getInstance(this);
        billingManager.addListener(this);
        buildUi();
        billingManager.startConnection();
        refreshBillingUi();
    }

    @Override
    protected void onDestroy() {
        billingManager.removeListener(this);
        executor.shutdownNow();
        recycleBitmap(sourceBitmap);
        recycleBitmap(convertedBitmap);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("圖片防窺轉檔 Pro", 26, true);
        title.setTextColor(Color.rgb(18, 18, 18));
        root.addView(title, fullWidth());

        TextView subtitle = text("將圖片轉成帶有防窺視覺效果的一般影像檔。處理完全在本機完成。", 14, false);
        subtitle.setTextColor(Color.rgb(75, 75, 75));
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle, fullWidth());

        unlockStatusText = cardText("");
        root.addView(unlockStatusText, fullWidth());

        productText = text("正在讀取商品資訊...", 14, false);
        productText.setTextColor(Color.rgb(70, 70, 70));
        productText.setPadding(0, dp(12), 0, dp(6));
        root.addView(productText, fullWidth());

        buyButton = button("購買圖片防窺轉檔 Pro", v -> billingManager.launchPurchase(this));
        root.addView(buyButton, fullWidth());
        restoreButton = button("恢復購買", v -> billingManager.queryExistingPurchases());
        root.addView(restoreButton, fullWidth());

        TextView notice = cardText("說明：此功能會降低圖片的可讀性，加入條紋遮罩、亮度量化、微雜訊與邊緣遮蔽。輸出的檔案仍是一般圖片，不能保證在所有螢幕、角度或拍攝距離下完全防窺。");
        notice.setTextColor(Color.rgb(82, 82, 82));
        root.addView(notice, fullWidth());

        root.addView(sectionTitle("1. 選擇圖片"));
        selectedImageText = text("尚未選擇圖片", 14, false);
        selectedImageText.setTextColor(Color.rgb(80, 80, 80));
        root.addView(selectedImageText, fullWidth());
        pickButton = button("選擇 HEIC / PNG / JPG / 其他圖片", v -> pickImage());
        root.addView(pickButton, fullWidth());

        root.addView(sectionTitle("2. 調整效果"));
        strengthText = text("", 14, false);
        root.addView(strengthText, fullWidth());
        strengthSeek = new SeekBar(this);
        strengthSeek.setMax(100);
        strengthSeek.setProgress(78);
        strengthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateStrengthLabel(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(strengthSeek, fullWidth());

        stripeText = text("", 14, false);
        root.addView(stripeText, fullWidth());
        stripeSeek = new SeekBar(this);
        stripeSeek.setMax(16);
        stripeSeek.setProgress(6);
        stripeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateStripeLabel(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(stripeSeek, fullWidth());
        updateStrengthLabel();
        updateStripeLabel();

        keepColorCheck = new CheckBox(this);
        keepColorCheck.setText("保留原圖片色彩");
        keepColorCheck.setTextSize(14);
        keepColorCheck.setChecked(true);
        root.addView(keepColorCheck, fullWidth());

        watermarkCheck = new CheckBox(this);
        watermarkCheck.setText("加入細微防窺紋理");
        watermarkCheck.setTextSize(14);
        watermarkCheck.setChecked(true);
        root.addView(watermarkCheck, fullWidth());

        convertButton = button("產生防窺圖片", v -> convertImage());
        root.addView(convertButton, fullWidth());

        root.addView(sectionTitle("3. 匯出"));
        formatSpinner = new Spinner(this);
        ArrayAdapter<OutputFormat> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                OutputFormat.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(adapter);
        root.addView(formatSpinner, fullWidth());

        saveButton = button("儲存轉換後圖片", v -> createOutputDocument());
        root.addView(saveButton, fullWidth());

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, fullWidth());

        processingStatusText = text("", 14, false);
        processingStatusText.setTextColor(Color.rgb(80, 80, 80));
        processingStatusText.setPadding(0, dp(8), 0, dp(8));
        root.addView(processingStatusText, fullWidth());

        previewImage = new ImageView(this);
        previewImage.setAdjustViewBounds(true);
        previewImage.setBackgroundColor(Color.rgb(238, 238, 238));
        previewImage.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(previewImage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        root.addView(sectionTitle("格式支援"));
        TextView hint = text("輸入：透過 Android 系統圖片解碼器讀取 image/*，常見格式包含 HEIC / HEIF / AVIF / WebP / PNG / JPEG / GIF / BMP。動態圖片會以可解碼的靜態畫面處理。\n\n輸出：PNG、JPG、WebP、HEIC / HEIF。HEIC 匯出能力會依裝置編碼支援而定。", 13, false);
        hint.setTextColor(Color.rgb(90, 90, 90));
        root.addView(hint, fullWidth());

        root.addView(button("返回主畫面", v -> finish()), fullWidth());
        setContentView(scroll);
    }

    private void pickImage() {
        if (!ensureUnlocked()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif",
                "image/heic", "image/heif", "image/heif-sequence", "image/heic-sequence",
                "image/avif", "image/bmp", "image/x-ms-bmp", "image/*"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(intent, "選擇圖片"), REQUEST_PICK_IMAGE);
    }

    private void convertImage() {
        if (!ensureUnlocked()) return;
        if (sourceBitmap == null) {
            Toast.makeText(this, "請先選擇圖片。", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, "正在產生防窺圖片...");
        final ImagePrivacyProcessor.Options options = new ImagePrivacyProcessor.Options();
        options.strengthPercent = strengthSeek.getProgress();
        options.stripePitchPx = 3 + stripeSeek.getProgress();
        options.keepColor = keepColorCheck.isChecked();
        options.addWatermark = watermarkCheck.isChecked();
        executor.execute(() -> {
            try {
                Bitmap output = ImagePrivacyProcessor.applyPrivacyEffect(sourceBitmap, options);
                mainHandler.post(() -> {
                    recycleBitmap(convertedBitmap);
                    convertedBitmap = output;
                    previewImage.setImageBitmap(convertedBitmap);
                    setBusy(false, "已產生防窺圖片：" + output.getWidth() + " x " + output.getHeight());
                    refreshBillingUi();
                });
            } catch (Exception e) {
                mainHandler.post(() -> setBusy(false, "轉換失敗：" + e.getMessage()));
            }
        });
    }

    private void createOutputDocument() {
        if (!ensureUnlocked()) return;
        if (convertedBitmap == null) {
            Toast.makeText(this, "請先產生防窺圖片。", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingOutputFormat = (OutputFormat) formatSpinner.getSelectedItem();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(pendingOutputFormat.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, "privacy_display_" + timestamp + pendingOutputFormat.extension);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CREATE_OUTPUT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_PICK_IMAGE) {
            try {
                int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
            }
            decodeSelectedImage(uri);
        } else if (requestCode == REQUEST_CREATE_OUTPUT) {
            saveConvertedImage(uri, pendingOutputFormat);
        }
    }

    private void decodeSelectedImage(Uri uri) {
        setBusy(true, "正在讀取圖片...");
        executor.execute(() -> {
            try {
                ImageDecoder.Source decoderSource = ImageDecoder.createSource(getContentResolver(), uri);
                Bitmap decoded = ImageDecoder.decodeBitmap(decoderSource, (decoder, info, src) -> {
                    Size size = info.getSize();
                    int width = size.getWidth();
                    int height = size.getHeight();
                    int max = Math.max(width, height);
                    if (max > MAX_DECODE_DIMENSION) {
                        float scale = MAX_DECODE_DIMENSION / (float) max;
                        decoder.setTargetSize(
                                Math.max(1, Math.round(width * scale)),
                                Math.max(1, Math.round(height * scale))
                        );
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                Bitmap safe = ensureArgb8888(decoded);
                if (safe != decoded) decoded.recycle();
                mainHandler.post(() -> {
                    recycleBitmap(sourceBitmap);
                    recycleBitmap(convertedBitmap);
                    sourceBitmap = safe;
                    convertedBitmap = null;
                    previewImage.setImageBitmap(sourceBitmap);
                    selectedImageText.setText("已選擇：" + displayUri(uri)
                            + "\n尺寸：" + sourceBitmap.getWidth() + " x " + sourceBitmap.getHeight());
                    setBusy(false, "圖片已載入，請調整效果後產生。");
                    refreshBillingUi();
                });
            } catch (Exception e) {
                mainHandler.post(() -> setBusy(false, "讀取圖片失敗，請換一張圖片或改用系統可解碼格式：" + e.getMessage()));
            }
        });
    }

    private void saveConvertedImage(Uri uri, OutputFormat format) {
        if (convertedBitmap == null) return;
        setBusy(true, "正在儲存 " + format.label + "...");
        executor.execute(() -> {
            try {
                if (format == OutputFormat.HEIC) {
                    writeHeic(uri, convertedBitmap);
                } else {
                    writeCompressed(uri, convertedBitmap, format);
                }
                mainHandler.post(() -> {
                    setBusy(false, "已儲存：" + format.label);
                    Toast.makeText(this, "圖片已儲存。", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> setBusy(false, "儲存失敗：" + e.getMessage()));
            }
        });
    }

    private void writeCompressed(Uri uri, Bitmap bitmap, OutputFormat format) throws IOException {
        Bitmap.CompressFormat compressFormat;
        int quality = 94;
        switch (format) {
            case JPEG:
                compressFormat = Bitmap.CompressFormat.JPEG;
                break;
            case WEBP:
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    compressFormat = Bitmap.CompressFormat.WEBP_LOSSY;
                } else {
                    compressFormat = Bitmap.CompressFormat.WEBP;
                }
                break;
            case PNG:
            default:
                compressFormat = Bitmap.CompressFormat.PNG;
                quality = 100;
                break;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new IOException("無法開啟輸出檔案。");
            boolean ok = bitmap.compress(compressFormat, quality, out);
            if (!ok) throw new IOException("圖片編碼失敗。");
            out.flush();
        }
    }

    private void writeHeic(Uri uri, Bitmap bitmap) throws Exception {
        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
        if (pfd == null) throw new IOException("無法開啟 HEIC 輸出檔案。");
        HeifWriter writer = null;
        try {
            writer = new HeifWriter.Builder(
                    pfd.getFileDescriptor(),
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    HeifWriter.INPUT_MODE_BITMAP
            )
                    .setQuality(92)
                    .setMaxImages(1)
                    .build();
            writer.start();
            writer.addBitmap(bitmap);
            writer.stop(0);
        } finally {
            if (writer != null) writer.close();
            pfd.close();
        }
    }

    private Bitmap ensureArgb8888(Bitmap bitmap) {
        if (bitmap == null) return null;
        if (bitmap.getConfig() == Bitmap.Config.ARGB_8888) return bitmap;
        Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (copy != null) return copy;
        Bitmap fallback = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        int[] row = new int[bitmap.getWidth()];
        for (int y = 0; y < bitmap.getHeight(); y++) {
            bitmap.getPixels(row, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
            fallback.setPixels(row, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
        }
        return fallback;
    }

    private boolean ensureUnlocked() {
        if (billingManager.isUnlocked()) return true;
        new AlertDialog.Builder(this)
                .setTitle("需要 Pro 解鎖")
                .setMessage("圖片防窺轉檔是一次性付費功能。請透過 Google Play 購買，或使用「恢復購買」同步既有授權。")
                .setPositiveButton("購買", (dialog, which) -> billingManager.launchPurchase(this))
                .setNegativeButton("取消", null)
                .show();
        return false;
    }

    private void refreshBillingUi() {
        boolean unlocked = billingManager.isUnlocked();
        unlockStatusText.setText(unlocked ? "✓ 圖片防窺 Pro 已解鎖" : "• 圖片防窺 Pro 尚未解鎖");
        productText.setText("商品 ID：" + BillingManager.IMAGE_PRIVACY_PRODUCT_ID
                + "\n價格：" + billingManager.getFormattedPrice());
        buyButton.setEnabled(!unlocked);
        restoreButton.setEnabled(true);
        pickButton.setEnabled(unlocked);
        convertButton.setEnabled(unlocked && sourceBitmap != null);
        saveButton.setEnabled(unlocked && convertedBitmap != null);
        strengthSeek.setEnabled(unlocked);
        stripeSeek.setEnabled(unlocked);
        keepColorCheck.setEnabled(unlocked);
        watermarkCheck.setEnabled(unlocked);
        formatSpinner.setEnabled(unlocked);
    }

    @Override
    public void onBillingStateChanged() {
        refreshBillingUi();
    }

    @Override
    public void onBillingMessage(String message) {
        if (message == null || message.trim().isEmpty() || processingStatusText == null) return;
        processingStatusText.setText(message);
    }

    private void setBusy(boolean busy, String message) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        processingStatusText.setText(message == null ? "" : message);
        if (pickButton != null) pickButton.setEnabled(!busy && billingManager.isUnlocked());
        if (convertButton != null) convertButton.setEnabled(!busy && billingManager.isUnlocked() && sourceBitmap != null);
        if (saveButton != null) saveButton.setEnabled(!busy && billingManager.isUnlocked() && convertedBitmap != null);
    }

    private void updateStrengthLabel() {
        if (strengthText != null) strengthText.setText("防窺強度：" + strengthSeek.getProgress() + "%");
    }

    private void updateStripeLabel() {
        if (stripeText != null) stripeText.setText("遮罩條紋寬度：" + (3 + stripeSeek.getProgress()) + " px");
    }

    private String displayUri(Uri uri) {
        if (uri == null) return "";
        String last = uri.getLastPathSegment();
        return last == null ? uri.toString() : last;
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
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
        if (bold) textView.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
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
}
