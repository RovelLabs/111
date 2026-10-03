package dev.pulseclient.mixin;

import dev.pulseclient.module.modules.world.Fullbright;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fullbright: для настройки «Яркость» отдаём значение больше максимального. */
@Mixin(SimpleOption.class)
public abstract class SimpleOptionMixin<T> {
    @SuppressWarnings("unchecked")
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void pulseclient$fullbright(CallbackInfoReturnable<T> cir) {
        if (!Fullbright.active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null && (Object) this == mc.options.getGamma()) {
            cir.setReturnValue((T) (Object) 16.0);
        }
    }
}
