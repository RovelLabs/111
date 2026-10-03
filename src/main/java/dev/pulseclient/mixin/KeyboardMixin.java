package dev.pulseclient.mixin;

import dev.pulseclient.PulseClient;
import dev.pulseclient.event.events.KeyEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void pulseclient$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Бинды работают только в самой игре, а не при наборе текста в чате или меню.
        if (window != mc.getWindow().getHandle() || mc.currentScreen != null) return;
        PulseClient.get().getEventBus().post(new KeyEvent(key, action));
    }
}
