package dev.pulseclient.module.modules.misc;

import dev.pulseclient.gui.ClickGuiScreen;
import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.setting.BooleanSetting;
import dev.pulseclient.setting.ColorSetting;
import org.lwjgl.glfw.GLFW;

/** Открывает меню клиента. По умолчанию — правый Shift. */
public final class ClickGui extends Module {
    public final ColorSetting accent = add(new ColorSetting("Акцентный цвет", 0xFF8A5CF6));
    public final BooleanSetting blur = add(new BooleanSetting("Затемнять фон", true));
    public final BooleanSetting descriptions = add(new BooleanSetting("Подсказки", true));

    public ClickGui() {
        super("ClickGUI", "Меню клиента и редактор HUD", Category.MISC);
        setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    protected void onEnable() {
        if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
        setEnabled(false); // модуль-«кнопка»: открыл меню и сразу выключился
    }
}
