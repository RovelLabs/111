package dev.pulseclient.module.modules.hud;

import dev.pulseclient.module.HudModule;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Активные эффекты с иконкой, уровнем и оставшимся временем. */
public final class PotionHud extends HudModule {
    private static final float LINE = 20;

    public final BooleanSetting hideVanilla = add(new BooleanSetting("Скрыть стандартные", true));

    public PotionHud() {
        super("Potions", "Активные эффекты и их время", 0.005f, 0.55f);
    }

    private List<StatusEffectInstance> effects() {
        return mc.player == null ? List.of() : new ArrayList<>(mc.player.getStatusEffects());
    }

    private String label(StatusEffectInstance effect) {
        String name = effect.getEffectType().getName().getString();
        int level = effect.getAmplifier() + 1;
        return level > 1 ? name + " " + level : name;
    }

    private static String time(StatusEffectInstance effect) {
        if (effect.isInfinite()) return "∞";
        int seconds = effect.getDuration() / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public float getWidth() {
        int max = 70;
        for (StatusEffectInstance e : effects()) max = Math.max(max, mc.textRenderer.getWidth(label(e)) + 30);
        return max;
    }

    @Override
    public float getHeight() {
        return Math.max(LINE, effects().size() * (LINE + 2));
    }

    @Override
    protected void render(DrawContext context, float x, float y, float tickDelta) {
        float yy = y;
        for (StatusEffectInstance effect : effects()) {
            RenderUtil.roundedRect(context, x, yy, getWidth(), LINE, 4, Theme.HUD_BG);
            int color = 0xFF000000 | effect.getEffectType().getColor();
            RenderUtil.roundedRect(context, x, yy + 3, 2, LINE - 6, 1, color);
            Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(effect.getEffectType());
            context.drawSprite((int) x + 4, (int) yy + 1, 0, 18, 18, sprite);
            context.drawTextWithShadow(mc.textRenderer, label(effect), (int) x + 25, (int) yy + 2, Theme.TEXT);
            context.drawTextWithShadow(mc.textRenderer, time(effect), (int) x + 25, (int) yy + 11, Theme.MUTED);
            yy += LINE + 2;
        }
    }
}
