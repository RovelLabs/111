package dev.pulseclient.module.modules.hud;

import dev.pulseclient.module.HudModule;
import dev.pulseclient.render.ColorUtil;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Плашка «Pulse» с ником, FPS, пингом и временем. */
public final class Watermark extends HudModule {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BooleanSetting showName = add(new BooleanSetting("Ник", true));
    private final BooleanSetting showFps = add(new BooleanSetting("FPS", true));
    private final BooleanSetting showPing = add(new BooleanSetting("Пинг", true));
    private final BooleanSetting showTime = add(new BooleanSetting("Время", true));

    private int frames, fps;
    private long fpsTimer = System.currentTimeMillis();

    public Watermark() {
        super("Watermark", "Название клиента, ник, FPS, пинг и время", 0.005f, 0.008f);
    }

    private String info() {
        StringBuilder text = new StringBuilder();
        if (showName.get()) text.append("  ").append(mc.getSession().getUsername());
        if (showFps.get()) text.append("  ").append(fps).append(" fps");
        if (showPing.get() && mc.getNetworkHandler() != null && mc.player != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null && !mc.isInSingleplayer()) text.append("  ").append(entry.getLatency()).append(" ms");
        }
        if (showTime.get()) text.append("  ").append(LocalTime.now().format(TIME));
        return text.toString();
    }

    @Override
    public float getWidth() {
        return mc.textRenderer.getWidth("Pulse") + mc.textRenderer.getWidth(info()) + 16;
    }

    @Override
    public float getHeight() {
        return 16;
    }

    @Override
    protected void render(DrawContext context, float x, float y, float tickDelta) {
        frames++;
        long now = System.currentTimeMillis();
        if (now - fpsTimer >= 1000) {
            fps = frames;
            frames = 0;
            fpsTimer = now;
        }
        float w = getWidth(), h = getHeight();
        RenderUtil.shadow(context, x, y, w, h, 5, 3, 0xFF000000);
        RenderUtil.roundedRect(context, x, y, w, h, 5, Theme.HUD_BG);
        RenderUtil.horizontalGradient(context, x + 4, y + h - 1.5f, w - 8, 1, Theme.accent(), Theme.accent2());

        float tx = x + 7;
        String brand = "Pulse";
        for (int i = 0; i < brand.length(); i++) {
            String ch = String.valueOf(brand.charAt(i));
            context.drawTextWithShadow(mc.textRenderer, ch, (int) tx, (int) y + 4, Theme.wave(i * 0.08f));
            tx += mc.textRenderer.getWidth(ch);
        }
        context.drawTextWithShadow(mc.textRenderer, info(), (int) tx, (int) y + 4, ColorUtil.withAlpha(Theme.TEXT, 230));
    }
}
