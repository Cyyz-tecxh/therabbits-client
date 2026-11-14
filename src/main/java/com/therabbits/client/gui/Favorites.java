package com.therabbits.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Modules you starred in the mods menu. */
public final class Favorites {
    private Favorites() {}

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("therabbits/favorites.json");
    private static final Set<String> SET = new LinkedHashSet<>();
    private static boolean loaded;

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) for (var e : JsonParser.parseString(Files.readString(FILE)).getAsJsonArray()) SET.add(e.getAsString());
        } catch (Exception ignored) {}
    }

    public static boolean is(String module) { load(); return SET.contains(module); }

    public static boolean any() { load(); return !SET.isEmpty(); }

    public static void toggle(String module) {
        load();
        if (!SET.remove(module)) SET.add(module);
        try {
            JsonArray a = new JsonArray();
            SET.forEach(a::add);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, a.toString());
        } catch (Exception ignored) {}
    }
}
