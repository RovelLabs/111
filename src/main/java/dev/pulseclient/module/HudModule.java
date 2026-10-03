package dev.pulseclient.module;

import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.Render2DEvent;
import dev.pulseclient.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/**
 * Элемент HUD с позицией на экране. Позиция хранится в долях экрана (0..1), поэтому
 * не съезжает при смене разрешения. Пока открыт ClickGUI, элементы можно перетаскивать.
 */
public abstract class HudModule extends Module {
    private final NumberSetting posX;
    private final NumberSetting posY;

    protected HudModule(String name, String description, float defaultX, float defaultY) {
        super(name, description, Category.HUD);
        posX = add(new NumberSetting("x", defaultX, 0, 1, 0.0001)).visibleWhen(() -> false);
        posY = add(new NumberSetting("y", defaultY, 0, 1, 0.0001)).visibleWhen(() -> false);
    }

    public abstract float getWidth();

    public abstract float getHeight();

    /** Рисует элемент в точке (x, y) в координатах интерфейса. */
    protected abstract void render(DrawContext context, float x, float y, float tickDelta);

    @Subscribe
    private void onRender2D(Render2DEvent event) {
        if (mc.options.debugEnabled || mc.player == null) return;
        render(event.context(), getX(), getY(), event.tickDelta());
    }

    private float screenWidth() {
        return mc.getWindow().getScaledWidth();
    }

    private float screenHeight() {
        return mc.getWindow().getScaledHeight();
    }

    public float getX() {
        return clamp(posX.getFloat() * screenWidth(), screenWidth() - getWidth());
    }

    public float getY() {
        return clamp(posY.getFloat() * screenHeight(), screenHeight() - getHeight());
    }

    public void setPosition(float x, float y) {
        posX.set((double) clamp(x, screenWidth() - getWidth()) / Math.max(1, screenWidth()));
        posY.set((double) clamp(y, screenHeight() - getHeight()) / Math.max(1, screenHeight()));
    }

    /** Элемент прижат к правому краю — тогда текст в нём выравнивается вправо. */
    protected boolean isOnRightSide() {
        return getX() + getWidth() / 2 > screenWidth() / 2;
    }

    private static float clamp(float value, float max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }
}
