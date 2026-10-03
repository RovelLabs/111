package dev.pulseclient.module.modules.hud;

import dev.pulseclient.module.HudModule;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

/** Координаты, направление взгляда и пересчёт для Ада/верхнего мира. */
public final class Coordinates extends HudModule {
    private final BooleanSetting direction = add(new BooleanSetting("Направление", true));
    private final BooleanSetting nether = add(new BooleanSetting("Координаты Ада", true));

    public Coordinates() {
        super("Coordinates", "Координаты, сторона света и пересчёт для Ада", 0.005f, 0.90f);
    }

    private String[] lines() {
        var p = mc.player;
        String main = String.format("XYZ  %.1f  %.1f  %.1f", p.getX(), p.getY(), p.getZ()).replace(',', '.');
        String dir = "";
        if (direction.get()) {
            dir = switch (p.getHorizontalFacing()) {
                case NORTH -> "Север (−Z)";
                case SOUTH -> "Юг (+Z)";
                case WEST -> "Запад (−X)";
                default -> "Восток (+X)";
            };
        }
        String other = "";
        if (nether.get() && mc.world != null) {
            boolean inNether = mc.world.getRegistryKey() == World.NETHER;
            double k = inNether ? 8 : 0.125;
            other = String.format("%s  %.0f  %.0f", inNether ? "Верхний мир" : "Ад", p.getX() * k, p.getZ() * k);
        }
        return new String[]{main, dir, other};
    }

    @Override
    public float getWidth() {
        if (mc.player == null) return 100;
        int max = 0;
        for (String s : lines()) max = Math.max(max, mc.textRenderer.getWidth(s));
        return max + 12;
    }

    @Override
    public float getHeight() {
        if (mc.player == null) return 14;
        int count = 0;
        for (String s : lines()) if (!s.isEmpty()) count++;
        return count * 10 + 6;
    }

    @Override
    protected void render(DrawContext context, float x, float y, float tickDelta) {
        RenderUtil.roundedRect(context, x, y, getWidth(), getHeight(), 4, Theme.HUD_BG);
        float yy = y + 4;
        boolean first = true;
        for (String s : lines()) {
            if (s.isEmpty()) continue;
            context.drawTextWithShadow(mc.textRenderer, s, (int) x + 6, (int) yy, first ? Theme.TEXT : Theme.MUTED);
            if (first) RenderUtil.rect(context, x + 2, yy, 1.5f, 8, Theme.accent());
            first = false;
            yy += 10;
        }
    }
}
