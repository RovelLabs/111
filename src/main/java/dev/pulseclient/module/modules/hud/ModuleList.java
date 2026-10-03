package dev.pulseclient.module.modules.hud;

import dev.pulseclient.PulseClient;
import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.Render2DEvent;
import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.setting.BooleanSetting;
import dev.pulseclient.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.Comparator;
import java.util.List;

/** Список включённых модулей в правом верхнем углу, от длинных к коротким. */
public final class ModuleList extends Module {
    private final BooleanSetting background = add(new BooleanSetting("Фон", true));
    private final BooleanSetting hideHud = add(new BooleanSetting("Скрывать HUD-модули", false));
    private final ColorSetting color = add(new ColorSetting("Цвет", 0xFF8A5CF6));

    public ModuleList() {
        super("ModuleList", "Список включённых модулей", Category.HUD);
    }

    @Subscribe
    private void onRender2D(Render2DEvent event) {
        if (mc.options.debugEnabled) return;

        List<String> names = PulseClient.get().getModuleManager().getAll().stream()
                .filter(Module::isEnabled)
                .filter(m -> !(hideHud.get() && m.getCategory() == Category.HUD))
                .map(Module::getName)
                .sorted(Comparator.comparingInt((String n) -> mc.textRenderer.getWidth(n)).reversed())
                .toList();

        DrawContext context = event.context();
        int screenWidth = context.getScaledWindowWidth();
        int lineHeight = mc.textRenderer.fontHeight + 2;
        int y = 2;
        for (String name : names) {
            int width = mc.textRenderer.getWidth(name);
            int x = screenWidth - width - 4;
            if (background.get()) context.fill(x - 2, y, screenWidth, y + lineHeight, 0x90000000);
            context.fill(screenWidth - 1, y, screenWidth, y + lineHeight, color.get());
            context.drawTextWithShadow(mc.textRenderer, name, x, y + 2, color.get());
            y += lineHeight;
        }
    }
}
