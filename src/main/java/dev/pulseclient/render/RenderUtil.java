package dev.pulseclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * 2D-примитивы для GUI и HUD. Скруглённые углы рисуются в реальных пикселях экрана
 * (а не в «пикселях» масштаба интерфейса), поэтому получаются гладкими.
 */
public final class RenderUtil {
    private RenderUtil() {
    }

    private static double scale() {
        return MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    public static void rect(DrawContext context, float x, float y, float w, float h, int color) {
        roundedRect(context, x, y, w, h, 0, color);
    }

    public static void roundedRect(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || ColorUtil.alpha(color) == 0) return;
        double s = scale();
        int px = (int) Math.round(x * s), py = (int) Math.round(y * s);
        int pw = (int) Math.round(w * s), ph = (int) Math.round(h * s);
        int r = (int) Math.min(Math.round(radius * s), Math.min(pw, ph) / 2);

        context.getMatrices().push();
        context.getMatrices().scale((float) (1 / s), (float) (1 / s), 1f);
        if (r <= 0) {
            context.fill(px, py, px + pw, py + ph, color);
        } else {
            context.fill(px, py + r, px + pw, py + ph - r, color);
            for (int i = 0; i < r; i++) {
                double dy = r - i - 0.5;
                int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
                context.fill(px + inset, py + i, px + pw - inset, py + i + 1, color);
                context.fill(px + inset, py + ph - i - 1, px + pw - inset, py + ph - i, color);
            }
        }
        context.getMatrices().pop();
    }

    /** Горизонтальный градиент слева направо (шагами по 1 реальному пикселю). */
    public static void horizontalGradient(DrawContext context, float x, float y, float w, float h, int left, int right) {
        double s = scale();
        int px = (int) Math.round(x * s), py = (int) Math.round(y * s);
        int pw = (int) Math.round(w * s), ph = (int) Math.round(h * s);
        context.getMatrices().push();
        context.getMatrices().scale((float) (1 / s), (float) (1 / s), 1f);
        int steps = Math.max(1, Math.min(pw, 64));
        for (int i = 0; i < steps; i++) {
            int x0 = px + pw * i / steps, x1 = px + pw * (i + 1) / steps;
            context.fill(x0, py, x1, py + ph, ColorUtil.mix(left, right, i / (float) Math.max(1, steps - 1)));
        }
        context.getMatrices().pop();
    }

    public static void outline(DrawContext context, float x, float y, float w, float h, float thickness, int color) {
        rect(context, x, y, w, thickness, color);
        rect(context, x, y + h - thickness, w, thickness, color);
        rect(context, x, y + thickness, thickness, h - thickness * 2, color);
        rect(context, x + w - thickness, y + thickness, thickness, h - thickness * 2, color);
    }

    /** Мягкая тень: несколько полупрозрачных скруглённых прямоугольников с расширением. */
    public static void shadow(DrawContext context, float x, float y, float w, float h, float radius, int layers, int color) {
        for (int i = layers; i >= 1; i--) {
            float grow = i * 1.2f;
            roundedRect(context, x - grow, y - grow, w + grow * 2, h + grow * 2, radius + grow,
                    ColorUtil.fade(color, 0.35f / layers));
        }
    }

    public static boolean hovered(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
