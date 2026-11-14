package com.therabbits.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** A key you set from the TheRabbits menu (click, then press a key; Esc clears). Saved with vanilla controls. */
public class KeySetting extends Setting<String> {
    private final KeyMapping mapping;

    public KeySetting(String name, KeyMapping mapping) {
        super(name, "");
        this.mapping = mapping;
    }

    public KeyMapping mapping() { return mapping; }

    public String display() {
        return mapping.isUnbound() ? "None" : mapping.getTranslatedKeyMessage().getString();
    }

    /** -1 clears the key. */
    public void bind(int glfwKey) {
        mapping.setKey(glfwKey < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(glfwKey));
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    @Override public JsonElement save() { return JsonNull.INSTANCE; }
    @Override public void load(JsonElement e) {}
}
