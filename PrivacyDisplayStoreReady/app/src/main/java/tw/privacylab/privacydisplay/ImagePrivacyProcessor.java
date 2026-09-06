package tw.privacylab.privacydisplay;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Produces a self-contained privacy-display style image effect.
 *
 * The output is still a normal bitmap: no hardware privacy filter can be embedded in an image file.
 * This processor intentionally lowers readability by adding louver-like vertical masks, luminance
 * flattening, micro-noise and edge camouflage so the image is harder to read at a glance or from a
 * distance, while still being shareable as a regular image.
 */
public final class ImagePrivacyProcessor {
    private ImagePrivacyProcessor() {}

    public static final class Options {
        public int strengthPercent = 78;
        public int stripePitchPx = 7;
        public boolean keepColor = true;
        public boolean addWatermark = true;
    }

    public static Bitmap applyPrivacyEffect(Bitmap source, Options options) {
        if (source == null) throw new IllegalArgumentException("source bitmap is null");
        Options safe = options == null ? new Options() : options;
        int width = source.getWidth();
        int height = source.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] row = new int[width];
        int strength = clamp(safe.strengthPercent, 0, 100);
        float s = strength / 100f;
        int pitch = Math.max(3, safe.stripePitchPx);
        int darkWidth = Math.max(1, Math.round(pitch * (0.42f + 0.22f * s)));
        int lightWidth = Math.max(1, pitch - darkWidth);
        int block = Math.max(2, Math.round(3 + 10 * s));
        int seed = width * 1103 + height * 9176 + pitch * 131;

        for (int y = 0; y < height; y++) {
            source.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int c = row[x];
                int a = Color.alpha(c);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                int lum = (r * 299 + g * 587 + b * 114) / 1000;

                // Reduce local detail and color contrast. This makes text/QR-like content less readable.
                int qLum = quantize(lum, Math.max(8, Math.round(10 + 42 * s)));
                r = mix(r, qLum, safe.keepColor ? 0.38f * s : 0.84f * s);
                g = mix(g, qLum, safe.keepColor ? 0.38f * s : 0.84f * s);
                b = mix(b, qLum, safe.keepColor ? 0.38f * s : 0.84f * s);

                // Simulate privacy-film louvers with phase-shifted vertical masks.
                int phase = (x + ((y / 5) % pitch)) % pitch;
                if (phase < darkWidth) {
                    float darken = 0.20f + 0.50f * s;
                    r = Math.round(r * (1f - darken));
                    g = Math.round(g * (1f - darken));
                    b = Math.round(b * (1f - darken));
                } else if (phase >= darkWidth + Math.max(0, lightWidth - 1)) {
                    float brighten = 0.06f + 0.08f * s;
                    r = clamp(Math.round(r + (255 - r) * brighten));
                    g = clamp(Math.round(g + (255 - g) * brighten));
                    b = clamp(Math.round(b + (255 - b) * brighten));
                }

                // Add a deterministic micro-noise grid. Deterministic means repeated exports match.
                int noise = pseudoNoise(x / block, y / block, seed) - 128;
                int delta = Math.round(noise * (0.10f + 0.18f * s));
                r = clamp(r + delta);
                g = clamp(g + delta);
                b = clamp(b + delta);

                // Soft vignette reduces edge information when someone glances from the side.
                float nx = Math.abs((x + 0.5f) / width - 0.5f) * 2f;
                float ny = Math.abs((y + 0.5f) / height - 0.5f) * 2f;
                float vignette = (float) Math.pow(Math.max(nx, ny), 1.7) * 0.22f * s;
                r = Math.round(r * (1f - vignette));
                g = Math.round(g * (1f - vignette));
                b = Math.round(b * (1f - vignette));

                row[x] = Color.argb(a, clamp(r), clamp(g), clamp(b));
            }
            result.setPixels(row, 0, width, 0, y, width, 1);
        }

        if (safe.addWatermark) {
            applySubtleWatermark(result, s);
        }
        return result;
    }

    private static void applySubtleWatermark(Bitmap bitmap, float strength) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int period = Math.max(28, Math.round(Math.min(width, height) / 9f));
        int line = Math.max(1, Math.round(period / 24f));
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            boolean markRow = (y % period) < line;
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                boolean markDiag = ((x + y) % period) < line;
                if (!markRow && !markDiag) continue;
                int c = row[x];
                int a = Color.alpha(c);
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                float add = 9f + 11f * strength;
                row[x] = Color.argb(a, clamp(Math.round(r + add)), clamp(Math.round(g + add)), clamp(Math.round(b + add)));
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1);
        }
    }

    private static int quantize(int value, int step) {
        int safeStep = Math.max(1, step);
        return clamp(((value + safeStep / 2) / safeStep) * safeStep);
    }

    private static int pseudoNoise(int x, int y, int seed) {
        int n = x * 374761393 + y * 668265263 + seed * 1442695041;
        n = (n ^ (n >>> 13)) * 1274126177;
        return (n ^ (n >>> 16)) & 0xff;
    }

    private static int mix(int from, int to, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return clamp(Math.round(from + (to - from) * a));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
