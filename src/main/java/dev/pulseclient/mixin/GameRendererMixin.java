package dev.pulseclient.mixin;

import dev.pulseclient.PulseClient;
import dev.pulseclient.module.modules.player.Zoom;
import dev.pulseclient.module.modules.render.NoHurtCam;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void pulseclient$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        Zoom zoom = PulseClient.get().getModuleManager().get(Zoom.class);
        if (!zoom.isEnabled()) return;
        double divider = zoom.currentDivider();
        if (divider > 1.001) cir.setReturnValue(cir.getReturnValue() / divider);
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void pulseclient$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (PulseClient.get().getModuleManager().get(NoHurtCam.class).isEnabled()) ci.cancel();
    }
}
