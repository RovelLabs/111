package dev.pulseclient.mixin;

import dev.pulseclient.gui.PulseMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Во всех меню вне мира (настройки, выбор мира, сервера) вместо земляных блоков — затемнённый фон Pulse. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "renderBackgroundTexture", at = @At("HEAD"), cancellable = true)
    private void pulseclient$background(DrawContext context, CallbackInfo ci) {
        PulseMenu.drawBackground(context, width, height, width / 2, height / 2, 1f);
        context.fill(0, 0, width, height, 0x99050314);
        ci.cancel();
    }
}
