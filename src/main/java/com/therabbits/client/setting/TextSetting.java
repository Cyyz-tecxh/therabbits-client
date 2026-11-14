package com.therabbits.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text the user types in the menu. */
public class TextSetting extends Setting<String> {
    private final int maxLength;

    public TextSetting(String name, String value, int maxLength) {
        super(name, value);
        this.maxLength = maxLength;
    }

    public int maxLength() { return maxLength; }

    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { if (e.isJsonPrimitive()) value = e.getAsString(); }
}
