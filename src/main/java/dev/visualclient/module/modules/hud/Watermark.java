package dev.visualclient.module.modules.hud;

import dev.visualclient.VisualClient;
import dev.visualclient.event.Subscribe;
import dev.visualclient.event.events.Render2DEvent;
import dev.visualclient.module.Category;
import dev.visualclient.module.Module;
import dev.visualclient.setting.BooleanSetting;
import dev.visualclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Плашка с названием клиента, ником, FPS и временем в левом верхнем углу. */
public final class Watermark extends Module {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BooleanSetting showName = add(new BooleanSetting("Ник", true));
    private final BooleanSetting showFps = add(new BooleanSetting("FPS", true));
    private final BooleanSetting showTime = add(new BooleanSetting("Время", true));
    private final ColorSetting accent = add(new ColorSetting("Акцент", 0xFF8A5CF6));

    private int frames;
    private int fps;
    private long fpsTimer = System.currentTimeMillis();

    public Watermark() {
        super("Watermark", "Название клиента и полезная информация", Category.HUD);
    }

    @Subscribe
    private void onRender2D(Render2DEvent event) {
        countFps();
        if (mc.options.debugEnabled) return; // не перекрываем F3

        StringBuilder text = new StringBuilder(VisualClient.NAME);
        if (showName.get()) text.append(" | ").append(mc.getSession().getUsername());
        if (showFps.get()) text.append(" | ").append(fps).append(" fps");
        if (showTime.get()) text.append(" | ").append(LocalTime.now().format(TIME));

        DrawContext context = event.context();
        int x = 4, y = 4, padding = 4;
        int width = mc.textRenderer.getWidth(text.toString()) + padding * 2;
        int height = mc.textRenderer.fontHeight + padding * 2;

        context.fill(x, y, x + width, y + height, 0x90000000);
        context.fill(x, y, x + width, y + 1, accent.get());
        context.drawTextWithShadow(mc.textRenderer, text.toString(), x + padding, y + padding + 1, 0xFFFFFFFF);
    }

    private void countFps() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - fpsTimer >= 1000) {
            fps = frames;
            frames = 0;
            fpsTimer = now;
        }
    }
}
