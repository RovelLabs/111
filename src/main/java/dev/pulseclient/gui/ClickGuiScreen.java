package dev.pulseclient.gui;

import dev.pulseclient.PulseClient;
import dev.pulseclient.config.ConfigManager;
import dev.pulseclient.module.Category;
import dev.pulseclient.module.HudModule;
import dev.pulseclient.module.Module;
import dev.pulseclient.module.modules.misc.ClickGui;
import dev.pulseclient.render.Animation;
import dev.pulseclient.render.ColorUtil;
import dev.pulseclient.render.RenderUtil;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import dev.pulseclient.setting.ColorSetting;
import dev.pulseclient.setting.ModeSetting;
import dev.pulseclient.setting.NumberSetting;
import dev.pulseclient.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Меню клиента: панели по категориям, модули включаются ЛКМ, настройки раскрываются ПКМ,
 * бинд — СКМ по модулю. Панели и элементы HUD перетаскиваются мышью.
 */
public final class ClickGuiScreen extends Screen {
    private static final float PANEL_WIDTH = 112, HEADER = 18, ROW = 15, SETTING_ROW = 13, RADIUS = 5;
    /** Положения панелей и раскрытые модули запоминаются между открытиями меню. */
    private static final Map<Category, float[]> PANEL_POS = new EnumMap<>(Category.class);
    private static final Map<Module, Animation> EXPAND = new HashMap<>();

    private final Animation open = new Animation(0, 14);
    private final Map<Module, Animation> hover = new HashMap<>();
    private Category draggingPanel;
    private float dragOffsetX, dragOffsetY;
    private HudModule draggingHud;
    private NumberSetting draggingSlider;
    private ColorSetting draggingHue;
    private float sliderX, sliderW;
    private Module binding;
    private String tooltip;

    public ClickGuiScreen() {
        super(Text.literal("Pulse Client"));
        open.setTarget(1);
    }

    @Override
    protected void init() {
        float x = 12;
        for (Category category : Category.values()) {
            PANEL_POS.putIfAbsent(category, new float[]{x, 28});
            x += PANEL_WIDTH + 10;
        }
    }

    private ClickGui settings() {
        return PulseClient.get().getModuleManager().get(ClickGui.class);
    }

    private List<Module> modules(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : PulseClient.get().getModuleManager().getByCategory(category)) {
            if (!m.isHidden() || m instanceof ClickGui) list.add(m);
        }
        return list;
    }

    private Animation expand(Module module) {
        return EXPAND.computeIfAbsent(module, m -> new Animation(0, 16));
    }

    // ------------------------------------------------------------ отрисовка

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float a = open.get();
        tooltip = null;
        if (settings().blur.get()) {
            context.fillGradient(0, 0, width, height, ColorUtil.fade(0xB0080614, a), ColorUtil.fade(0xD0140A2A, a));
        }

        renderHudEditor(context, mouseX, mouseY, a);

        context.getMatrices().push();
        // лёгкое «выезжание» сверху при открытии
        context.getMatrices().translate(0, (1 - a) * -12, 0);
        for (Category category : Category.values()) {
            renderPanel(context, category, mouseX, mouseY, a);
        }
        context.getMatrices().pop();

        renderFooter(context, a);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHudEditor(DrawContext context, int mouseX, int mouseY, float a) {
        for (Module m : PulseClient.get().getModuleManager().getAll()) {
            if (!(m instanceof HudModule hud) || !hud.isEnabled()) continue;
            float x = hud.getX(), y = hud.getY(), w = hud.getWidth(), h = hud.getHeight();
            boolean over = hud == draggingHud || RenderUtil.hovered(mouseX, mouseY, x, y, w, h);
            RenderUtil.outline(context, x - 1, y - 1, w + 2, h + 2, 0.5f,
                    ColorUtil.fade(over ? Theme.accent() : 0x66FFFFFF, a));
            if (over) tooltip = hud.getName() + " — перетащите, чтобы передвинуть";
        }
    }

    private void renderPanel(DrawContext context, Category category, int mouseX, int mouseY, float a) {
        float[] pos = PANEL_POS.get(category);
        float x = pos[0], y = pos[1];
        List<Module> list = modules(category);
        float height = HEADER + 4;
        for (Module m : list) height += ROW + settingsHeight(m) * expand(m).get();

        RenderUtil.shadow(context, x, y, PANEL_WIDTH, height, RADIUS, 4, ColorUtil.fade(0xFF000000, a));
        RenderUtil.roundedRect(context, x, y, PANEL_WIDTH, height, RADIUS, ColorUtil.fade(Theme.PANEL, a));
        RenderUtil.roundedRect(context, x, y, PANEL_WIDTH, HEADER, RADIUS, ColorUtil.fade(Theme.PANEL_HEADER, a));
        RenderUtil.rect(context, x, y + HEADER - RADIUS, PANEL_WIDTH, RADIUS, ColorUtil.fade(Theme.PANEL_HEADER, a));
        RenderUtil.horizontalGradient(context, x + 6, y + HEADER - 1, PANEL_WIDTH - 12, 1,
                ColorUtil.fade(Theme.accent(), a), ColorUtil.fade(Theme.accent2(), a));
        context.drawTextWithShadow(textRenderer, category.getIcon() + " " + category.getDisplayName(),
                (int) x + 7, (int) y + 5, ColorUtil.fade(Theme.TEXT, a));
        String count = String.valueOf(list.stream().filter(Module::isEnabled).count());
        context.drawText(textRenderer, count, (int) (x + PANEL_WIDTH - 7 - textRenderer.getWidth(count)), (int) y + 5,
                ColorUtil.fade(Theme.MUTED, a), false);

        float rowY = y + HEADER + 2;
        for (Module m : list) {
            boolean over = RenderUtil.hovered(mouseX, mouseY, x, rowY, PANEL_WIDTH, ROW);
            Animation h = hover.computeIfAbsent(m, k -> new Animation(0, 18)).setTarget(over ? 1 : 0);
            float hv = h.get();
            if (m.isEnabled()) {
                RenderUtil.roundedRect(context, x + 3, rowY + 1, PANEL_WIDTH - 6, ROW - 2, 3,
                        ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 60 + (int) (40 * hv)), a));
                RenderUtil.roundedRect(context, x + 3, rowY + 3, 2, ROW - 6, 1, ColorUtil.fade(Theme.accent(), a));
            } else if (hv > 0) {
                RenderUtil.roundedRect(context, x + 3, rowY + 1, PANEL_WIDTH - 6, ROW - 2, 3,
                        ColorUtil.fade(ColorUtil.fade(Theme.ROW_HOVER, hv), a));
            }
            String label = binding == m ? "Нажмите клавишу…" : m.getName();
            int color = m.isEnabled() ? Theme.TEXT : Theme.MUTED;
            context.drawText(textRenderer, label, (int) (x + 9 + hv * 2), (int) rowY + 4, ColorUtil.fade(color, a), false);
            String right = m.getKey() != GLFW.GLFW_KEY_UNKNOWN && binding != m ? keyName(m.getKey())
                    : (m.getSettings().stream().anyMatch(Setting::isVisible) ? (expand(m).get() > 0.5f ? "−" : "+") : "");
            context.drawText(textRenderer, right, (int) (x + PANEL_WIDTH - 8 - textRenderer.getWidth(right)), (int) rowY + 4,
                    ColorUtil.fade(Theme.MUTED, a), false);
            if (over && settings().descriptions.get()) tooltip = m.getDescription();
            rowY += ROW;

            float e = expand(m).get();
            if (e > 0.001f) {
                float full = settingsHeight(m);
                context.enableScissor((int) x, (int) rowY, (int) (x + PANEL_WIDTH), (int) (rowY + full * e) + 1);
                float sy = rowY;
                for (Setting<?> s : m.getSettings()) {
                    if (!s.isVisible()) continue;
                    renderSetting(context, s, x + 8, sy, PANEL_WIDTH - 16, mouseX, mouseY, a);
                    sy += SETTING_ROW;
                }
                context.disableScissor();
                rowY += full * e;
            }
        }
    }

    private float settingsHeight(Module m) {
        return m.getSettings().stream().filter(Setting::isVisible).count() * SETTING_ROW;
    }

    private void renderSetting(DrawContext context, Setting<?> s, float x, float y, float w, int mouseX, int mouseY, float a) {
        int text = ColorUtil.fade(Theme.TEXT, a), muted = ColorUtil.fade(Theme.MUTED, a), accent = ColorUtil.fade(Theme.accent(), a);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(0.85f, 0.85f, 1);
        context.drawText(textRenderer, s.getName(), 0, 3, muted, false);
        context.getMatrices().pop();

        if (s instanceof BooleanSetting b) {
            float sw = 14, sh = 7, sx = x + w - sw, sy = y + 3;
            RenderUtil.roundedRect(context, sx, sy, sw, sh, 3.5f, b.get() ? accent : ColorUtil.fade(0xFF3A3650, a));
            float knob = b.get() ? sx + sw - sh : sx;
            RenderUtil.roundedRect(context, knob + 1, sy + 1, sh - 2, sh - 2, 2.5f, text);
        } else if (s instanceof NumberSetting n) {
            String value = format(n);
            float bx = x + w * 0.45f, bw = w * 0.55f - textRenderer.getWidth(value) * 0.85f - 3;
            float t = (float) ((n.get() - n.getMin()) / (n.getMax() - n.getMin()));
            RenderUtil.roundedRect(context, bx, y + 5, bw, 3, 1.5f, ColorUtil.fade(0xFF3A3650, a));
            RenderUtil.roundedRect(context, bx, y + 5, bw * t, 3, 1.5f, accent);
            RenderUtil.roundedRect(context, bx + bw * t - 2, y + 4, 4, 5, 2, text);
            drawSmall(context, value, x + w - textRenderer.getWidth(value) * 0.85f, y + 3, text);
            if (draggingSlider == n) {
                sliderX = bx;
                sliderW = bw;
            }
        } else if (s instanceof ModeSetting m) {
            String value = "‹ " + m.get() + " ›";
            drawSmall(context, value, x + w - textRenderer.getWidth(value) * 0.85f, y + 3, accent);
        } else if (s instanceof ColorSetting c) {
            float bx = x + w * 0.45f, bw = w * 0.55f - 10;
            int steps = 24;
            for (int i = 0; i < steps; i++) {
                RenderUtil.rect(context, bx + bw * i / steps, y + 4, bw / steps + 0.5f, 5,
                        ColorUtil.fade(ColorUtil.hsv(i / (float) steps, 0.65f, 1f, 255), a));
            }
            float hue = ColorUtil.hue(c.get());
            RenderUtil.rect(context, bx + bw * hue - 0.5f, y + 3, 1, 7, text);
            RenderUtil.roundedRect(context, x + w - 7, y + 3, 7, 7, 2, ColorUtil.fade(c.get(), a));
            if (draggingHue == c) {
                sliderX = bx;
                sliderW = bw;
            }
        }
    }

    private void drawSmall(DrawContext context, String text, float x, float y, int color) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(0.85f, 0.85f, 1);
        context.drawText(textRenderer, text, 0, 0, color, false);
        context.getMatrices().pop();
    }

    private void renderFooter(DrawContext context, float a) {
        String hint = tooltip != null ? tooltip
                : "ЛКМ — вкл/выкл  ·  ПКМ — настройки  ·  СКМ — бинд  ·  тащите панели и HUD";
        int w = textRenderer.getWidth(hint) + 16;
        float x = (width - w) / 2f, y = height - 22;
        RenderUtil.roundedRect(context, x, y, w, 15, 7, ColorUtil.fade(0xD0161424, a));
        context.drawText(textRenderer, hint, (int) x + 8, (int) y + 4, ColorUtil.fade(Theme.TEXT, a), false);
        String brand = "Pulse Client " + PulseClient.VERSION;
        context.drawText(textRenderer, brand, 6, height - 12, ColorUtil.fade(Theme.wave(0), a), true);
    }

    private static String format(NumberSetting n) {
        double v = n.get();
        return n.getStep() >= 1 ? String.valueOf((long) v) : String.format("%.2f", v).replace(',', '.');
    }

    private static String keyName(int key) {
        String name = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
        return name.length() > 8 ? name.substring(0, 8) : name;
    }

    // ------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (binding != null) {
            binding = null;
            return true;
        }
        for (Category category : reversedCategories()) {
            float[] pos = PANEL_POS.get(category);
            float x = pos[0], y = pos[1];
            if (RenderUtil.hovered(mouseX, mouseY, x, y, PANEL_WIDTH, HEADER)) {
                draggingPanel = category;
                dragOffsetX = (float) mouseX - x;
                dragOffsetY = (float) mouseY - y;
                return true;
            }
            float rowY = y + HEADER + 2;
            for (Module m : modules(category)) {
                if (RenderUtil.hovered(mouseX, mouseY, x, rowY, PANEL_WIDTH, ROW)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        if (m instanceof ClickGui) expand(m).setTarget(expand(m).get() > 0.5f ? 0 : 1);
                        else m.toggle();
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        expand(m).setTarget(expand(m).get() > 0.5f ? 0 : 1);
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                        binding = m;
                    }
                    return true;
                }
                rowY += ROW;
                float e = expand(m).get();
                if (e > 0.5f) {
                    float sy = rowY;
                    for (Setting<?> s : m.getSettings()) {
                        if (!s.isVisible()) continue;
                        if (RenderUtil.hovered(mouseX, mouseY, x, sy, PANEL_WIDTH, SETTING_ROW)) {
                            clickSetting(s, button, mouseX, x + 8, PANEL_WIDTH - 16);
                            return true;
                        }
                        sy += SETTING_ROW;
                    }
                }
                rowY += settingsHeight(m) * e;
            }
        }
        // элементы HUD
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (Module m : PulseClient.get().getModuleManager().getAll()) {
                if (m instanceof HudModule hud && hud.isEnabled()
                        && RenderUtil.hovered(mouseX, mouseY, hud.getX(), hud.getY(), hud.getWidth(), hud.getHeight())) {
                    draggingHud = hud;
                    dragOffsetX = (float) mouseX - hud.getX();
                    dragOffsetY = (float) mouseY - hud.getY();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<Category> reversedCategories() {
        List<Category> list = new ArrayList<>(List.of(Category.values()));
        java.util.Collections.reverse(list);
        return list;
    }

    private void clickSetting(Setting<?> s, int button, double mouseX, float x, float w) {
        if (s instanceof BooleanSetting b) {
            b.toggle();
        } else if (s instanceof ModeSetting m) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                List<String> modes = m.getModes();
                m.set(modes.get((modes.indexOf(m.get()) - 1 + modes.size()) % modes.size()));
            } else {
                m.cycle();
            }
        } else if (s instanceof NumberSetting n) {
            draggingSlider = n;
            sliderX = x + w * 0.45f;
            sliderW = w * 0.55f - textRenderer.getWidth(format(n)) * 0.85f - 3;
            updateSlider(mouseX);
        } else if (s instanceof ColorSetting c) {
            draggingHue = c;
            sliderX = x + w * 0.45f;
            sliderW = w * 0.55f - 10;
            updateSlider(mouseX);
        }
    }

    private void updateSlider(double mouseX) {
        float t = (float) Math.max(0, Math.min(1, (mouseX - sliderX) / Math.max(1, sliderW)));
        if (draggingSlider != null) {
            draggingSlider.set(draggingSlider.getMin() + (draggingSlider.getMax() - draggingSlider.getMin()) * t);
        } else if (draggingHue != null) {
            draggingHue.set(ColorUtil.hsv(Math.min(t, 0.999f), 0.65f, 1f, draggingHue.getAlpha()));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingPanel != null) {
            float[] pos = PANEL_POS.get(draggingPanel);
            pos[0] = Math.max(0, Math.min(width - PANEL_WIDTH, (float) mouseX - dragOffsetX));
            pos[1] = Math.max(0, Math.min(height - HEADER, (float) mouseY - dragOffsetY));
            return true;
        }
        if (draggingHud != null) {
            draggingHud.setPosition((float) mouseX - dragOffsetX, (float) mouseY - dragOffsetY);
            return true;
        }
        if (draggingSlider != null || draggingHue != null) {
            updateSlider(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPanel = null;
        draggingHud = null;
        draggingSlider = null;
        draggingHue = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // прокрутка двигает все панели — если их много или экран маленький
        for (float[] pos : PANEL_POS.values()) pos[1] += (float) amount * 12;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding != null) {
            boolean clear = keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                    || keyCode == GLFW.GLFW_KEY_BACKSPACE;
            binding.setKey(clear ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
            binding = null;
            return true;
        }
        int guiKey = settings().getKey();
        if (keyCode == guiKey && guiKey != GLFW.GLFW_KEY_UNKNOWN) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        PulseClient.get().getConfigManager().save(ConfigManager.DEFAULT);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
