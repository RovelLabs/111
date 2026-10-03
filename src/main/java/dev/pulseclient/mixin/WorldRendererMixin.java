package dev.pulseclient.mixin;

import dev.pulseclient.PulseClient;
import dev.pulseclient.module.modules.render.BlockOverlay;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Block Overlay: свой цвет контура выделенного блока. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @ModifyArgs(method = "drawBlockOutline", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;drawCuboidShapeOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/util/shape/VoxelShape;DDDFFFF)V"))
    private void pulseclient$outlineColor(Args args) {
        BlockOverlay overlay = PulseClient.get().getModuleManager().get(BlockOverlay.class);
        if (!overlay.isEnabled()) return;
        int c = overlay.currentColor();
        args.set(6, ((c >> 16) & 0xFF) / 255f);
        args.set(7, ((c >> 8) & 0xFF) / 255f);
        args.set(8, (c & 0xFF) / 255f);
        args.set(9, overlay.opacity.getFloat());
    }
}
