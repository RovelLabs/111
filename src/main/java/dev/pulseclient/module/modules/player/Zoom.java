package dev.pulseclient.module.modules.player;

import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.render.Animation;
import dev.pulseclient.setting.NumberSetting;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Приближение, пока зажата клавиша C (как в OptiFine). FOV меняется в GameRendererMixin. */
public final class Zoom extends Module {
    private final NumberSetting factor = add(new NumberSetting("Кратность", 4, 1.5, 10, 0.5));
    private final Animation animation = new Animation(1, 14);

    public Zoom() {
        super("Zoom", "Приближение на клавишу C", Category.PLAYER);
    }

    /** Во сколько раз уменьшить FOV прямо сейчас (1 — без зума). */
    public double currentDivider() {
        boolean held = mc.currentScreen == null && mc.getWindow() != null
                && InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_C);
        return animation.setTarget(held ? factor.getFloat() : 1f).get();
    }
}
