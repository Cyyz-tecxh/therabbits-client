package com.therabbits.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A setting that cycles through named options (shown as "‹ option ›" in the menu). */
public class ModeSetting extends Setting<Integer> {
    private final String[] modes;
    private final boolean color;

    public ModeSetting(String name, int value, boolean color, String... modes) {
        super(name, value);
        this.modes = modes;
        this.color = color;
    }

    public String mode() { return modes[value]; }
    private java.util.function.IntFunction<String> label;
    /** What the menu shows for the current option (defaults to its name). */
    public String display() { return label == null ? mode() : label.apply(value); }
    public ModeSetting labels(java.util.function.IntFunction<String> label) { this.label = label; return this; }
    public boolean isColor() { return color; }
    public void next() { value = (value + 1) % modes.length; }
    public void previous() { value = (value - 1 + modes.length) % modes.length; }

    @Override public JsonElement save() { return new JsonPrimitive(mode()); }

    @Override
    public void load(JsonElement e) {
        if (!e.isJsonPrimitive()) return;
        String s = e.getAsString();
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(s)) value = i;
    }
}
