package dev.visualclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** Выбор одного варианта из списка. */
public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name, defaultValue);
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultValue)) {
            throw new IllegalArgumentException("Режим по умолчанию отсутствует в списке: " + defaultValue);
        }
    }

    public boolean is(String mode) {
        return get().equals(mode);
    }

    public List<String> getModes() {
        return modes;
    }

    public void cycle() {
        set(modes.get((modes.indexOf(get()) + 1) % modes.size()));
    }

    @Override
    public void set(String value) {
        if (modes.contains(value)) super.set(value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        set(json.getAsString());
    }
}
