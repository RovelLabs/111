package dev.pulseclient.module.modules.hud;

import dev.pulseclient.module.HudModule;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/** Надетая броня и предмет в руке с прочностью. */
public final class ArmorHud extends HudModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND};

    private final BooleanSetting percent = add(new BooleanSetting("Прочность в %", true));
    private final BooleanSetting background = add(new BooleanSetting("Фон", true));

    public ArmorHud() {
        super("ArmorHUD", "Броня и предмет в руке с прочностью", 0.60f, 0.86f);
    }

    @Override
    public float getWidth() {
        return SLOTS.length * 20 + 4;
    }

    @Override
    public float getHeight() {
        return percent.get() ? 28 : 20;
    }

    @Override
    protected void render(DrawContext context, float x, float y, float tickDelta) {
        if (background.get()) RenderUtil.roundedRect(context, x, y, getWidth(), getHeight(), 4, Theme.HUD_BG);
        float xx = x + 2;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                context.drawItem(stack, (int) xx + 1, (int) y + 2);
                context.drawItemInSlot(mc.textRenderer, stack, (int) xx + 1, (int) y + 2);
                if (percent.get() && stack.isDamageable()) {
                    int left = Math.round(100f * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage());
                    String text = left + "%";
                    int color = left > 50 ? 0xFF7EE787 : left > 20 ? 0xFFFFD166 : 0xFFFF6B6B;
                    context.getMatrices().push();
                    context.getMatrices().translate(xx + 10 - mc.textRenderer.getWidth(text) * 0.35f, y + 20, 200);
                    context.getMatrices().scale(0.7f, 0.7f, 1);
                    context.drawTextWithShadow(mc.textRenderer, text, 0, 0, color);
                    context.getMatrices().pop();
                }
            }
            xx += 20;
        }
    }
}
