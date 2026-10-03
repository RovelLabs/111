package dev.pulseclient.module.modules.world;

import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;

/** Максимальная яркость. Только в одиночной игре — на серверах это преимущество. */
public final class Fullbright extends Module {
    /** Читается из SimpleOptionMixin на каждом кадре — поэтому простой статический флаг. */
    public static volatile boolean active;

    public Fullbright() {
        super("Fullbright", "Полная яркость (только в одиночной игре)", Category.WORLD);
    }

    @Override
    public boolean isSingleplayerOnly() {
        return true;
    }

    @Override
    protected void onEnable() {
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
    }
}
