package dev.visualclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Цвет в формате ARGB (0xAARRGGBB). */
public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, int argb) {
        super(name, argb);
    }

    public int getAlpha() {
        return get() >>> 24;
    }

    public int getRed() {
        return (get() >> 16) & 0xFF;
    }

    public int getGreen() {
        return (get() >> 8) & 0xFF;
    }

    public int getBlue() {
        return get() & 0xFF;
    }

    /** Тот же цвет с другой прозрачностью, alpha в диапазоне 0..1. */
    public int withAlpha(float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (get() & 0x00FFFFFF);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(String.format("#%08X", get()));
    }

    @Override
    public void fromJson(JsonElement json) {
        set((int) Long.parseLong(json.getAsString().replace("#", ""), 16));
    }
}
