package dev.pulseclient.mixin;

import dev.pulseclient.PulseClient;
import dev.pulseclient.module.modules.world.TimeChanger;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Changer: подменяем время суток только для отрисовки на клиенте. */
@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void pulseclient$time(CallbackInfoReturnable<Long> cir) {
        PulseClient client = PulseClient.get();
        if (client == null) return;
        TimeChanger changer = client.getModuleManager().get(TimeChanger.class);
        if (changer.isEnabled()) cir.setReturnValue(changer.time());
    }
}
