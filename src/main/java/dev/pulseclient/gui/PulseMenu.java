package dev.pulseclient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pulseclient.PulseClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/** Фирменное главное меню: фоны Pulse с плавной сменой и параллаксом, логотип вместо надписи MINECRAFT. */
public final class PulseMenu {
    private static final Identifier[] BACKGROUNDS = {
            new Identifier("pulseclient", "textures/gui/background_1.png"),
            new Identifier("pulseclient", "textures/gui/background_2.png"),
            new Identifier("pulseclient", "textures/gui/background_3.png"),
    };
    private static final int BG_WIDTH = 1920, BG_HEIGHT = 1080;
    private static final Identifier LOGO = new Identifier("pulseclient", "textures/gui/logo.png");
    private static final int LOGO_WIDTH = 784, LOGO_HEIGHT = 354;

    /** Сколько показывается один фон и сколько длится переход, мс. */
    private static final long SHOW_MS = 12_000, FADE_MS = 2_000;
    private static final long START = Util.getMeasuringTimeMs();
    private static final int FIRST = (int) (Math.random() * BACKGROUNDS.length);
    private static boolean announced;

    private PulseMenu() {
    }

    public static void drawTitleBackground(DrawContext context, int width, int height, int mouseX, int mouseY,
                                           float alpha) {
        if (!announced) {
            announced = true;
            PulseClient.LOGGER.info("Pulse главное меню активно");
        }
        drawBackground(context, width, height, mouseX, mouseY, alpha);
    }

    public static void drawBackground(DrawContext context, int width, int height, int mouseX, int mouseY, float alpha) {
        long elapsed = Util.getMeasuringTimeMs() - START;
        int index = (int) ((elapsed / SHOW_MS + FIRST) % BACKGROUNDS.length);
        long inSlide = elapsed % SHOW_MS;

        context.fill(0, 0, width, height, 0xFF07051A);
        RenderSystem.enableBlend();
        draw(context, BACKGROUNDS[index], width, height, mouseX, mouseY, alpha);
        if (inSlide > SHOW_MS - FADE_MS) {
            float t = (inSlide - (SHOW_MS - FADE_MS)) / (float) FADE_MS;
            draw(context, BACKGROUNDS[(index + 1) % BACKGROUNDS.length], width, height, mouseX, mouseY, alpha * smooth(t));
        }
        context.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void draw(DrawContext context, Identifier texture, int width, int height, int mouseX, int mouseY,
                             float alpha) {
        // Картинка с запасом 6% закрывает экран целиком и чуть сдвигается за мышью
        float scale = Math.max(width / (float) BG_WIDTH, height / (float) BG_HEIGHT) * 1.06f;
        int w = Math.round(BG_WIDTH * scale), h = Math.round(BG_HEIGHT * scale);
        float dx = (mouseX - width / 2f) / Math.max(1, width) * (w - width) * 0.8f;
        float dy = (mouseY - height / 2f) / Math.max(1, height) * (h - height) * 0.8f;
        int x = Math.round((width - w) / 2f - dx), y = Math.round((height - h) / 2f - dy);
        context.setShaderColor(1f, 1f, 1f, alpha);
        context.drawTexture(texture, x, y, w, h, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);
    }

    public static void drawLogo(DrawContext context, int screenWidth, float alpha) {
        int w = Math.min(200, Math.round(screenWidth * 0.55f));
        int h = Math.round(w * LOGO_HEIGHT / (float) LOGO_WIDTH);
        // лёгкое «дыхание» логотипа в ритме пульса
        float beat = (float) Math.pow(Math.max(0, Math.sin((Util.getMeasuringTimeMs() - START) / 1000.0 * Math.PI)), 12);
        int grow = Math.round(beat * 4);
        RenderSystem.enableBlend();
        context.setShaderColor(1f, 1f, 1f, alpha);
        context.drawTexture(LOGO, (screenWidth - w) / 2 - grow, 12 - grow / 2, w + grow * 2, h + grow,
                0, 0, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
        context.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
