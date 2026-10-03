package dev.pulseclient.module.modules.hud;

import dev.pulseclient.PulseClient;
import dev.pulseclient.module.HudModule;
import dev.pulseclient.module.Module;
import dev.pulseclient.render.Animation;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ArrayList — список включённых модулей с переливающимся цветом и плавным появлением. */
public final class ModuleList extends HudModule {
    private static final float LINE = 11;

    private final BooleanSetting background = add(new BooleanSetting("Фон", true));
    private final BooleanSetting bar = add(new BooleanSetting("Полоска", true));
    private final BooleanSetting hideHud = add(new BooleanSetting("Скрывать HUD-модули", true));
    private final Map<Module, Animation> slide = new HashMap<>();

    public ModuleList() {
        super("ArrayList", "Список включённых модулей", 0.995f, 0.008f);
    }

    private List<Module> shown() {
        return PulseClient.get().getModuleManager().getAll().stream()
                .filter(m -> !m.isHidden())
                .filter(m -> !(hideHud.get() && m instanceof HudModule))
                .filter(m -> m.isEnabled() || slide.containsKey(m) && slide.get(m).get() > 0.02f)
                .sorted(Comparator.comparingInt((Module m) -> mc.textRenderer.getWidth(m.getName())).reversed())
                .toList();
    }

    @Override
    public float getWidth() {
        return Math.max(60, shown().stream().mapToInt(m -> mc.textRenderer.getWidth(m.getName())).max().orElse(0) + 10);
    }

    @Override
    public float getHeight() {
        return Math.max(LINE, shown().size() * LINE);
    }

    @Override
    protected void render(DrawContext context, float x, float y, float tickDelta) {
        boolean right = isOnRightSide();
        float width = getWidth();
        float yy = y;
        int index = 0;
        for (Module m : shown()) {
            float t = slide.computeIfAbsent(m, k -> new Animation(0, 14)).setTarget(m.isEnabled() ? 1 : 0).get();
            String name = m.getName();
            float w = mc.textRenderer.getWidth(name) + 8;
            float bx = right ? x + width - w * t : x - w + w * t;
            int color = Theme.wave(index * 0.07f);
            if (background.get()) RenderUtil.rect(context, bx, yy, w, LINE * t, Theme.HUD_BG);
            if (bar.get()) RenderUtil.rect(context, right ? x + width - 1.5f : x, yy, 1.5f, LINE * t, color);
            if (t > 0.5f) context.drawTextWithShadow(mc.textRenderer, name, (int) (bx + 4), (int) (yy + 2), color);
            yy += LINE * t;
            index++;
        }
    }
}
