package dev.visualclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Число со слайдером: значение всегда в пределах [min, max] и кратно step. */
public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void set(Double value) {
        double snapped = Math.round(Math.round(value / step) * step * 1e6) / 1e6; // убираем хвосты вида 1.2000000000000002
        super.set(Math.max(min, Math.min(max, snapped)));
    }

    public float getFloat() {
        return get().floatValue();
    }

    public int getInt() {
        return get().intValue();
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        set(json.getAsDouble());
    }
}
