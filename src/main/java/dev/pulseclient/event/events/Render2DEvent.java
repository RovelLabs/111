package dev.pulseclient.event.events;

import dev.pulseclient.event.Event;
import net.minecraft.client.gui.DrawContext;

/** Отрисовка поверх HUD, каждый кадр. */
public final class Render2DEvent extends Event {
    private final DrawContext context;
    private final float tickDelta;

    public Render2DEvent(DrawContext context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
    }

    public DrawContext context() {
        return context;
    }

    public float tickDelta() {
        return tickDelta;
    }
}
