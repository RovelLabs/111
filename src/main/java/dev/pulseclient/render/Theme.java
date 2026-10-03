package dev.pulseclient.render;

import dev.pulseclient.PulseClient;
import dev.pulseclient.module.modules.misc.ClickGui;

/** Фирменные цвета. Акцентный цвет берётся из настроек модуля ClickGUI. */
public final class Theme {
    public static final int BACKGROUND = 0xE6100E1C;
    public static final int PANEL = 0xF2161424;
    public static final int PANEL_HEADER = 0xFF1D1A30;
    public static final int ROW_HOVER = 0x22FFFFFF;
    public static final int TEXT = 0xFFF2F2F7;
    public static final int MUTED = 0xFF9C98B8;
    public static final int HUD_BG = 0x9A0E0C1A;

    private Theme() {
    }

    public static int accent() {
        ClickGui gui = PulseClient.get() == null ? null : PulseClient.get().getModuleManager().get(ClickGui.class);
        return gui == null ? 0xFF8A5CF6 : gui.accent.get();
    }

    /** Второй цвет градиента — акцент, сдвинутый по оттенку. */
    public static int accent2() {
        int a = accent();
        return ColorUtil.hsv(ColorUtil.hue(a) + 0.08f, 0.55f, 1f, 255);
    }

    /** Переливающийся цвет для полос и списков: offset — сдвиг по позиции элемента. */
    public static int wave(float offset) {
        double t = (System.currentTimeMillis() % 3000L) / 3000.0;
        float k = (float) (0.5 + 0.5 * Math.sin((t + offset) * Math.PI * 2));
        return ColorUtil.mix(accent(), accent2(), k);
    }
}
