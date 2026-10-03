package dev.pulseclient.mixin;

import dev.pulseclient.PulseClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /** Заголовок окна: «Pulse Client 0.1.x — Minecraft 1.20.1». */
    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void pulseclient$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(PulseClient.NAME + " " + PulseClient.VERSION + " — Minecraft "
                + SharedConstants.getGameVersion().getName());
    }
}
