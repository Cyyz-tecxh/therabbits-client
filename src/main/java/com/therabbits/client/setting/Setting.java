package com.therabbits.client.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
    private final String name;
    protected T value;
    private final T def;

    protected Setting(String name, T value) {
        this.name = name;
        this.value = value;
        this.def = value;
    }

    /** True while the setting still has the value it started with. */
    public boolean isDefault() { return java.util.Objects.equals(value, def); }
    /** Back to the starting value. */
    public void reset() { set(def); }

    public String name() { return name; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }

    public abstract JsonElement save();
    public abstract void load(JsonElement element);
}
