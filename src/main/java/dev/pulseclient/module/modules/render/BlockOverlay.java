package dev.pulseclient.module.modules.render;

import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import dev.pulseclient.setting.ColorSetting;
import dev.pulseclient.setting.NumberSetting;

/** Цвет контура выделенного блока. Применяется в WorldRendererMixin. */
public final class BlockOverlay extends Module {
    public final BooleanSetting useAccent = add(new BooleanSetting("Цвет клиента", true));
    public final ColorSetting color = add(new ColorSetting("Цвет", 0xFF8A5CF6)).visibleWhen(() -> !useAccent.get());
    public final NumberSetting opacity = add(new NumberSetting("Непрозрачность", 0.8, 0.2, 1.0, 0.05));

    public BlockOverlay() {
        super("Block Overlay", "Цветной контур выделенного блока", Category.RENDER);
    }

    public int currentColor() {
        return useAccent.get() ? Theme.wave(0) : color.get();
    }
}
