package com.therabbits.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A button in the settings page (nothing saved). */
public class ActionSetting extends Setting<String> {
    private final Runnable action;

    public ActionSetting(String name, String buttonLabel, Runnable action) {
        super(name, buttonLabel);
        this.action = action;
    }

    public void run() { action.run(); }

    @Override public JsonElement save() { return JsonNull.INSTANCE; }
    @Override public void load(JsonElement e) {}
}
