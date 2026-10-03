package dev.pulseclient.mixin;

import dev.pulseclient.gui.PulseMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Вместо крутящейся панорамы — наши фоны, вместо надписи MINECRAFT — логотип Pulse. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/RotatingCubeMapRenderer;render(FF)V"))
    private void pulseclient$background(RotatingCubeMapRenderer panorama, float delta, float alpha,
                                        DrawContext context, int mouseX, int mouseY, float tickDelta) {
        PulseMenu.drawTitleBackground(context, this.width, this.height, mouseX, mouseY, alpha);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"))
    private void pulseclient$logo(LogoDrawer logoDrawer, DrawContext context, int screenWidth, float alpha) {
        PulseMenu.drawLogo(context, screenWidth, alpha);
    }
}
