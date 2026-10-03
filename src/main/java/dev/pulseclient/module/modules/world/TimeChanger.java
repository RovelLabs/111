package dev.pulseclient.module.modules.world;

import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.setting.ModeSetting;
import dev.pulseclient.setting.NumberSetting;

/** Своё время суток только у вас на экране. Применяется в ClientWorldPropertiesMixin. */
public final class TimeChanger extends Module {
    private final ModeSetting mode = add(new ModeSetting("Время", "Закат", "Утро", "День", "Закат", "Ночь", "Своё"));
    private final NumberSetting custom = add(new NumberSetting("Тики", 13000, 0, 23999, 100)).visibleWhen(() -> mode.is("Своё"));

    public TimeChanger() {
        super("Time Changer", "Своё время суток (видно только вам)", Category.WORLD);
    }

    public long time() {
        return switch (mode.get()) {
            case "Утро" -> 0;
            case "День" -> 6000;
            case "Ночь" -> 18000;
            case "Своё" -> custom.get().longValue();
            default -> 12800;
        };
    }
}
