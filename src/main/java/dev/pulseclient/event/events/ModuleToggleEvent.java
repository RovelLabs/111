package dev.pulseclient.event.events;

import dev.pulseclient.event.Event;
import dev.pulseclient.module.Module;

/** Модуль включили или выключили — для уведомлений. */
public final class ModuleToggleEvent extends Event {
    private final Module module;
    private final boolean enabled;

    public ModuleToggleEvent(Module module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
    }

    public Module module() {
        return module;
    }

    public boolean enabled() {
        return enabled;
    }
}
