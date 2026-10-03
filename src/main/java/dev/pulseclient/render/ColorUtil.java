package dev.pulseclient.render;

import java.awt.Color;

public final class ColorUtil {
    private ColorUtil() {
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    /** Умножает прозрачность цвета на factor (0..1). */
    public static int fade(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static int hsv(float hue, float saturation, float brightness, int alpha) {
        return withAlpha(Color.HSBtoRGB(hue, saturation, brightness), alpha);
    }

    public static float hue(int argb) {
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        return hsb[0];
    }

    public static float[] rgba(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                (argb >>> 24) / 255f};
    }
}
