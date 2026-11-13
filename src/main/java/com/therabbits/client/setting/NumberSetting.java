package com.therabbits.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
    private final double min, max, step;

    public NumberSetting(String name, double value, double min, double max, double step) {
        super(name, value);
        this.min = min; this.max = max; this.step = step;
    }

    public double min() { return min; }
    public double max() { return max; }
    public float f() { return value.floatValue(); }
    public int i() { return (int) Math.round(value); }

    @Override
    public void set(Double v) {
        double snapped = Math.round(v / step) * step;
        value = Math.max(min, Math.min(max, snapped));
    }

    /** 0..1 position of the slider knob. */
    public double fraction() { return (value - min) / (max - min); }
    public void setFraction(double f) { set(min + Math.max(0, Math.min(1, f)) * (max - min)); }

    public String display() {
        return step >= 1 ? String.valueOf(i()) : String.format("%.2f", value);
    }

    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { if (e.isJsonPrimitive()) set(e.getAsDouble()); }
}
