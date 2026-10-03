package dev.pulseclient.mixin;

import dev.pulseclient.PulseClient;
import dev.pulseclient.module.modules.player.ViewModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ViewModel: сдвиг и масштаб руки/предмета от первого лица. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void pulseclient$push(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                  float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.push();
        ViewModel vm = PulseClient.get().getModuleManager().get(ViewModel.class);
        if (!vm.isEnabled()) return;
        float side = hand == Hand.MAIN_HAND ? 1 : -1;
        matrices.translate(vm.x.getFloat() * side, vm.y.getFloat(), vm.z.getFloat());
        float s = vm.scale.getFloat();
        matrices.scale(s, s, s);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void pulseclient$pop(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                 float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.pop();
    }
}
