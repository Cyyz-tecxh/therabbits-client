package com.therabbits.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.function.Supplier;

/** A read-only line in the settings page (label on the left, live text on the right). */
public class InfoSetting extends Setting<String> {
    private final Supplier<String> text;

    public InfoSetting(String name, Supplier<String> text) {
        super(name, "");
        this.text = text;
    }

    public String text() {
        try { String t = text.get(); return t == null ? "" : t; } catch (Exception e) { return ""; }
    }

    @Override public JsonElement save() { return JsonNull.INSTANCE; }
    @Override public void load(JsonElement e) {}
}
