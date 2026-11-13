package com.therabbits.client.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
    private final String name;
    protected T value;

    protected Setting(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public String name() { return name; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }

    public abstract JsonElement save();
    public abstract void load(JsonElement element);
}
