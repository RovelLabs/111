package dev.visualclient.module;

import dev.visualclient.event.EventBus;
import dev.visualclient.event.Subscribe;
import dev.visualclient.event.events.KeyEvent;
import dev.visualclient.event.events.TickEvent;
import dev.visualclient.module.modules.hud.ModuleList;
import dev.visualclient.module.modules.hud.Watermark;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private final Map<Class<? extends Module>, Module> modules = new LinkedHashMap<>();

    public ModuleManager(EventBus eventBus) {
        register(new Watermark());
        register(new ModuleList());

        eventBus.subscribe(this);
    }

    private void register(Module module) {
        modules.put(module.getClass(), module);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        return (T) modules.get(type);
    }

    public Module getByName(String name) {
        for (Module module : modules.values()) {
            if (module.getName().equalsIgnoreCase(name)) return module;
        }
        return null;
    }

    public List<Module> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    public List<Module> getByCategory(Category category) {
        return modules.values().stream().filter(m -> m.getCategory() == category).toList();
    }

    @Subscribe
    private void onKey(KeyEvent event) {
        if (event.action() != GLFW.GLFW_PRESS || event.key() == GLFW.GLFW_KEY_UNKNOWN) return;
        for (Module module : modules.values()) {
            if (module.getKey() == event.key()) module.toggle();
        }
    }

    /** Выключаем singleplayer-only модули, как только игрок оказался на сервере. */
    @Subscribe
    private void onTick(TickEvent event) {
        for (Module module : modules.values()) {
            if (module.isEnabled() && !module.isAllowedHere()) module.setEnabled(false);
        }
    }
}
