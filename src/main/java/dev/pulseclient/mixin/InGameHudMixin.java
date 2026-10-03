package dev.pulseclient.mixin;

import dev.pulseclient.PulseClient;
import dev.pulseclient.module.modules.hud.PotionHud;
import dev.pulseclient.module.modules.render.CustomCrosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** Свой прицел — стандартный не рисуем. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void pulseclient$crosshair(DrawContext context, CallbackInfo ci) {
        if (PulseClient.get().getModuleManager().get(CustomCrosshair.class).isEnabled()) ci.cancel();
    }

    /** Свой список эффектов — стандартные иконки в углу прячем. */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void pulseclient$effects(DrawContext context, CallbackInfo ci) {
        PotionHud potions = PulseClient.get().getModuleManager().get(PotionHud.class);
        if (potions.isEnabled() && potions.hideVanilla.get()) ci.cancel();
    }
}
