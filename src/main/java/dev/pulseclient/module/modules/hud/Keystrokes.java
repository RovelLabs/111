package dev.pulseclient.module.modules.hud;

import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.MouseEvent;
import dev.pulseclient.module.HudModule;
import dev.pulseclient.render.Animation;
import dev.pulseclient.render.ColorUtil;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Клавиши WASD, пробел и кнопки мыши с CPS — подсвечиваются при нажатии. */
public final class Keystrokes extends HudModule {
    private static final float KEY = 20, GAP = 2;

    private final BooleanSetting mouse = add(new BooleanSetting("Кнопки мыши", true));
    private final BooleanSetting space = add(new BooleanSetting("Пробел", true));
    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();
    private final Map<String, Animation> press = new HashMap<>();

    public Keystrokes() {
        super("Keystrokes", "Нажатые клавиши и CPS", 0.005f, 0.30f);
    }

    @Subscribe
    private void onMouse(MouseEvent event) {
        if (event.action() != GLFW.GLFW_PRESS) return;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) leftClicks.add(System.currentTimeMillis());
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rightClicks.add(System.currentTimeMillis());
    }

    private static int cps(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
        return clicks.size();
    }

    @Override
    public float getWidth() {
        return KEY * 3 + GAP * 2;
    }

    @Override
    public float getHeight() {
        float h = KEY * 2 + GAP;
        if (mouse.get()) h += KEY + GAP;
        if (space.get()) h += 10 + GAP;
        return h;
    }

    @Override
    protected void render(DrawContext context, float x, float y, float tickDelta) {
        key(context, "W", mc.options.forwardKey, x + KEY + GAP, y, KEY, KEY);
        key(context, "A", mc.options.leftKey, x, y + KEY + GAP, KEY, KEY);
        key(context, "S", mc.options.backKey, x + KEY + GAP, y + KEY + GAP, KEY, KEY);
        key(context, "D", mc.options.rightKey, x + (KEY + GAP) * 2, y + KEY + GAP, KEY, KEY);
        float yy = y + (KEY + GAP) * 2;
        if (mouse.get()) {
            float half = (getWidth() - GAP) / 2;
            key(context, "ЛКМ " + cps(leftClicks), mc.options.attackKey, x, yy, half, KEY);
            key(context, "ПКМ " + cps(rightClicks), mc.options.useKey, x + half + GAP, yy, half, KEY);
            yy += KEY + GAP;
        }
        if (space.get()) key(context, "———", mc.options.jumpKey, x, yy, getWidth(), 10);
    }

    private void key(DrawContext context, String label, KeyBinding binding, float x, float y, float w, float h) {
        String id = binding.getTranslationKey();
        float t = press.computeIfAbsent(id, k -> new Animation(0, 22)).setTarget(binding.isPressed() ? 1 : 0).get();
        RenderUtil.roundedRect(context, x, y, w, h, 4, ColorUtil.mix(Theme.HUD_BG, ColorUtil.withAlpha(Theme.accent(), 200), t));
        int color = ColorUtil.mix(Theme.TEXT, 0xFFFFFFFF, t);
        context.drawTextWithShadow(mc.textRenderer, label, (int) (x + (w - mc.textRenderer.getWidth(label)) / 2),
                (int) (y + (h - 8) / 2 + 0.5f), color);
    }
}
