package dev.pulseclient.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/** Настройка модуля. Умеет сохраняться в JSON и показываться в ClickGUI. */
public abstract class Setting<T> {
    private final String name;
    private final T defaultValue;
    private T value;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void reset() {
        this.value = defaultValue;
    }

    /** Показывать настройку в GUI только при выполнении условия (например, при выбранном режиме). */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return (S) this;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement json);
}
