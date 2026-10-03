package dev.pulseclient.module.modules.render;

import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;

/** Убирает тряску камеры при получении урона. Работает через GameRendererMixin. */
public final class NoHurtCam extends Module {
    public NoHurtCam() {
        super("No Hurt Cam", "Без тряски камеры при уроне", Category.RENDER);
    }
}
