package dev.visualclient.event.events;

import dev.visualclient.event.Event;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;

/** Отрисовка в мире после всего остального, каждый кадр. Координаты — относительно камеры. */
public final class Render3DEvent extends Event {
    private final MatrixStack matrices;
    private final Camera camera;
    private final float tickDelta;

    public Render3DEvent(MatrixStack matrices, Camera camera, float tickDelta) {
        this.matrices = matrices;
        this.camera = camera;
        this.tickDelta = tickDelta;
    }

    public MatrixStack matrices() {
        return matrices;
    }

    public Camera camera() {
        return camera;
    }

    public float tickDelta() {
        return tickDelta;
    }
}
