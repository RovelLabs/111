package dev.pulseclient.module.modules.misc;

import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.ModuleToggleEvent;
import dev.pulseclient.event.events.Render2DEvent;
import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.render.Animation;
import dev.pulseclient.render.ColorUtil;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Всплывающие уведомления в правом нижнем углу при включении/выключении модулей. */
public final class Notifications extends Module {
    private static final long LIFETIME = 2200;
    private final List<Toast> toasts = new ArrayList<>();

    private static final class Toast {
        final String title;
        final boolean on;
        final long created = System.currentTimeMillis();
        final Animation slide = new Animation(0, 12).setTarget(1);
        final Animation y = new Animation(-1, 14);

        Toast(String title, boolean on) {
            this.title = title;
            this.on = on;
        }
    }

    public Notifications() {
        super("Notifications", "Уведомления о включении модулей", Category.MISC);
    }

    @Subscribe
    private void onToggle(ModuleToggleEvent event) {
        if (event.module() == this) return;
        toasts.add(new Toast(event.module().getName(), event.enabled()));
        if (toasts.size() > 5) toasts.remove(0);
    }

    @Subscribe
    private void onRender(Render2DEvent event) {
        DrawContext context = event.context();
        float sw = context.getScaledWindowWidth(), sh = context.getScaledWindowHeight();
        float h = 24, gap = 4;
        int index = 0;
        Iterator<Toast> it = toasts.iterator();
        List<Toast> alive = new ArrayList<>();
        while (it.hasNext()) {
            Toast t = it.next();
            long age = System.currentTimeMillis() - t.created;
            if (age > LIFETIME) t.slide.setTarget(0);
            if (age > LIFETIME + 600) {
                it.remove();
                continue;
            }
            alive.add(t);
        }
        for (int i = alive.size() - 1; i >= 0; i--) {
            Toast t = alive.get(i);
            float s = t.slide.get();
            String sub = t.on ? "включён" : "выключен";
            float w = Math.max(mc.textRenderer.getWidth(t.title), mc.textRenderer.getWidth(sub)) + 24;
            float targetY = sh - 30 - index * (h + gap);
            if (t.y.get() < 0) t.y.snap(targetY);
            float y = t.y.setTarget(targetY).get();
            float x = sw - (w + 6) * s;
            RenderUtil.roundedRect(context, x, y, w, h, 5, ColorUtil.fade(0xE6141222, s));
            int dot = t.on ? Theme.accent() : 0xFF6B6680;
            RenderUtil.roundedRect(context, x + 6, y + 8, 7, 7, 3.5f, ColorUtil.fade(dot, s));
            context.drawTextWithShadow(mc.textRenderer, t.title, (int) x + 18, (int) y + 4, ColorUtil.fade(Theme.TEXT, s));
            context.drawText(mc.textRenderer, sub, (int) x + 18, (int) y + 14, ColorUtil.fade(Theme.MUTED, s), false);
            long age = System.currentTimeMillis() - t.created;
            float left = 1f - Math.min(1f, age / (float) LIFETIME);
            RenderUtil.rect(context, x + 4, y + h - 1.5f, (w - 8) * left, 1, ColorUtil.fade(Theme.accent(), s));
            index++;
        }
    }
}
