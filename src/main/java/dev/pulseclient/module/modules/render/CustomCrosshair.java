package dev.pulseclient.module.modules.render;

import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.Render2DEvent;
import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import dev.pulseclient.setting.ColorSetting;
import dev.pulseclient.setting.ModeSetting;
import dev.pulseclient.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/** Свой прицел вместо стандартного. Стандартный скрывается в InGameHudMixin. */
public final class CustomCrosshair extends Module {
    private final ModeSetting style = add(new ModeSetting("Вид", "Крест", "Крест", "Крест с точкой", "Точка", "Круг"));
    private final NumberSetting size = add(new NumberSetting("Размер", 4, 1, 10, 0.5));
    private final NumberSetting gap = add(new NumberSetting("Отступ", 2, 0, 6, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Толщина", 1, 0.5, 3, 0.5));
    private final BooleanSetting dynamic = add(new BooleanSetting("Расходится при беге", true));
    private final BooleanSetting useAccent = add(new BooleanSetting("Цвет клиента", true));
    private final ColorSetting color = add(new ColorSetting("Цвет", 0xFFFFFFFF)).visibleWhen(() -> !useAccent.get());

    public CustomCrosshair() {
        super("Crosshair", "Свой прицел: крест, точка или круг", Category.RENDER);
    }

    @Subscribe
    private void onRender(Render2DEvent event) {
        if (mc.player == null || !mc.options.getPerspective().isFirstPerson() || mc.options.debugEnabled) return;
        DrawContext context = event.context();
        float cx = context.getScaledWindowWidth() / 2f, cy = context.getScaledWindowHeight() / 2f;
        int c = useAccent.get() ? Theme.accent() : color.get();
        float t = thickness.getFloat(), s = size.getFloat();
        float g = gap.getFloat();
        if (dynamic.get() && mc.player.getVelocity().horizontalLengthSquared() > 0.003) g += 1.5f;

        switch (style.get()) {
            case "Точка" -> RenderUtil.roundedRect(context, cx - t, cy - t, t * 2, t * 2, t, c);
            case "Круг" -> {
                int seg = 32;
                float r = s + g;
                for (int i = 0; i < seg; i++) {
                    double a = Math.PI * 2 * i / seg;
                    RenderUtil.rect(context, cx + (float) Math.cos(a) * r - t / 2, cy + (float) Math.sin(a) * r - t / 2, t, t, c);
                }
            }
            default -> {
                RenderUtil.rect(context, cx - t / 2, cy - g - s, t, s, c);
                RenderUtil.rect(context, cx - t / 2, cy + g, t, s, c);
                RenderUtil.rect(context, cx - g - s, cy - t / 2, s, t, c);
                RenderUtil.rect(context, cx + g, cy - t / 2, s, t, c);
                if (style.is("Крест с точкой")) RenderUtil.rect(context, cx - t / 2, cy - t / 2, t, t, c);
            }
        }
    }
}
