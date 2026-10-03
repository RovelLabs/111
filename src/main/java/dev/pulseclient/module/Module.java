package dev.pulseclient.module;

import dev.pulseclient.PulseClient;
import dev.pulseclient.event.events.ModuleToggleEvent;
import dev.pulseclient.setting.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Базовый класс модуля. Пока модуль включён, он подписан на шину событий
 * и получает события в методах с {@link dev.pulseclient.event.Subscribe}.
 */
public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private int key = GLFW.GLFW_KEY_UNKNOWN;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    /** Регистрирует настройку; удобно вызывать прямо при объявлении поля. */
    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    /**
     * Модули, которые были бы нечестными в мультиплеере (FullBright, Freecam и т.п.),
     * возвращают true — тогда клиент сам выключает их вне одиночного мира.
     */
    public boolean isSingleplayerOnly() {
        return false;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        if (enabled && isSingleplayerOnly() && !isAllowedHere()) return;

        this.enabled = enabled;
        if (enabled) {
            PulseClient.get().getEventBus().subscribe(this);
            onEnable();
        } else {
            PulseClient.get().getEventBus().unsubscribe(this);
            onDisable();
        }
        if (!isHidden()) PulseClient.get().getEventBus().post(new ModuleToggleEvent(this, this.enabled));
    }

    /** Служебные модули (ClickGUI) не показываются в списке модулей и уведомлениях. */
    public boolean isHidden() {
        return false;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    /** Можно ли включать модуль в текущей ситуации (для singleplayer-only модулей). */
    public boolean isAllowedHere() {
        return !isSingleplayerOnly() || mc.world == null || mc.isInSingleplayer();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }
}
