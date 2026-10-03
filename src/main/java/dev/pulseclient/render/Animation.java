package dev.pulseclient.render;

/** Плавное приближение значения к цели, не зависящее от FPS. */
public final class Animation {
    private float value;
    private float target;
    private final float speed;
    private long last = System.nanoTime();

    /** speed — во сколько раз за секунду сокращается расстояние до цели (8–20 — приятно). */
    public Animation(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public Animation setTarget(float target) {
        this.target = target;
        return this;
    }

    public float get() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        value += (target - value) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - value) < 0.001f) value = target;
        return value;
    }

    public void snap(float v) {
        value = v;
        target = v;
    }
}
