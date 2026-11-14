package com.therabbits.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("therabbits.json");

    private Config() {}

    /** Everything (toggles, settings, HUD positions) as JSON. */
    public static JsonObject toJson() {
        JsonObject root = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled());
            if (m instanceof HudModule h) {
                o.addProperty("x", h.x);
                o.addProperty("y", h.y);
            }
            JsonObject s = new JsonObject();
            for (Setting<?> setting : m.settings()) s.add(setting.name(), setting.save());
            o.add("settings", s);
            root.add(m.name(), o);
        }
        return root;
    }

    /** Apply JSON produced by toJson(); unknown entries are ignored. */
    public static void apply(JsonObject root) {
        for (Module m : ModuleManager.all()) {
            if (!root.has(m.name())) continue;
            JsonObject o = root.getAsJsonObject(m.name());
            if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
            if (m instanceof HudModule h) {
                if (o.has("x")) h.x = o.get("x").getAsDouble();
                if (o.has("y")) h.y = o.get("y").getAsDouble();
            }
            if (o.has("settings")) {
                JsonObject s = o.getAsJsonObject("settings");
                for (Setting<?> setting : m.settings())
                    if (s.has(setting.name())) setting.load(s.get(setting.name()));
            }
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(toJson()));
        } catch (Exception e) {
            RabbitsClient.LOG.error("Could not save config", e);
        }
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            apply(JsonParser.parseString(Files.readString(FILE)).getAsJsonObject());
        } catch (Exception e) {
            RabbitsClient.LOG.error("Could not load config", e);
        }
    }

    public static Gson gson() { return GSON; }
}
