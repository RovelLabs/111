package dev.pulseclient.module.modules.player;

import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.setting.NumberSetting;

/** Положение и размер рук/предметов от первого лица. Применяется в HeldItemRendererMixin. */
public final class ViewModel extends Module {
    public final NumberSetting x = add(new NumberSetting("Сдвиг X", 0, -1, 1, 0.05));
    public final NumberSetting y = add(new NumberSetting("Сдвиг Y", 0, -1, 1, 0.05));
    public final NumberSetting z = add(new NumberSetting("Сдвиг Z", 0, -1, 1, 0.05));
    public final NumberSetting scale = add(new NumberSetting("Размер", 1, 0.4, 1.6, 0.05));

    public ViewModel() {
        super("ViewModel", "Положение и размер рук от первого лица", Category.PLAYER);
    }
}
