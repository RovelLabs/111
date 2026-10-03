package dev.pulseclient.module;

import dev.pulseclient.event.EventBus;
import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.KeyEvent;
import dev.pulseclient.event.events.TickEvent;
import dev.pulseclient.module.modules.hud.ArmorHud;
import dev.pulseclient.module.modules.hud.Coordinates;
import dev.pulseclient.module.modules.hud.Keystrokes;
import dev.pulseclient.module.modules.hud.ModuleList;
import dev.pulseclient.module.modules.hud.PotionHud;
import dev.pulseclient.module.modules.hud.Watermark;
import dev.pulseclient.module.modules.misc.ClickGui;
import dev.pulseclient.module.modules.misc.Notifications;
import dev.pulseclient.module.modules.player.ViewModel;
import dev.pulseclient.module.modules.player.Zoom;
import dev.pulseclient.module.modules.render.BlockOverlay;
import dev.pulseclient.module.modules.render.ChinaHat;
import dev.pulseclient.module.modules.render.CustomCrosshair;
import dev.pulseclient.module.modules.render.HitParticles;
import dev.pulseclient.module.modules.render.JumpCircles;
import dev.pulseclient.module.modules.render.NoHurtCam;
import dev.pulseclient.module.modules.world.Fullbright;
import dev.pulseclient.module.modules.world.TimeChanger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private final Map<Class<? extends Module>, Module> modules = new LinkedHashMap<>();

    public ModuleManager(EventBus eventBus) {
        // HUD
        register(new Watermark());
        register(new ModuleList());
        register(new Keystrokes());
        register(new ArmorHud());
        register(new PotionHud());
        register(new Coordinates());
        // Визуалы
        register(new CustomCrosshair());
        register(new ChinaHat());
        register(new JumpCircles());
        register(new HitParticles());
        register(new BlockOverlay());
        register(new NoHurtCam());
        // Игрок
        register(new ViewModel());
        register(new Zoom());
        // Мир
        register(new TimeChanger());
        register(new Fullbright());
        // Разное
        register(new Notifications());
        register(new ClickGui());

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
