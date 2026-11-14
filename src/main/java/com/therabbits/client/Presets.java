package com.therabbits.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.module.Module;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** Built-in presets (which features are on) and 3 custom save slots (everything). */
public final class Presets {
    private Presets() {}

    public record BuiltIn(String name, String description, Set<String> enabled) {}

    /** Modules a built-in preset never changes (look & feel, not gameplay). */
    private static final Set<String> KEEP = Set.of("HUD Style", "Menu Background", "Client Font", "Block Outline", "Enchant Glint", "Alt Hider");

    public static final List<BuiltIn> BUILT_IN = List.of(
            new BuiltIn("PvP", "Everything useful in a fight", Set.of(
                    "FPS", "Ping", "Keystrokes", "Armor HUD", "Potion Effects", "Item Counter",
                    "Toggle Sprint", "Zoom", "Crosshair", "Reach Display", "Totem Pops", "CPS", "Combo Counter",
                    "No Hurt Cam", "Hide Fire Overlay", "Low Health Warning", "Freelook")),
            new BuiltIn("Minimal", "Just FPS, ping, armor and potions", Set.of(
                    "FPS", "Ping", "Armor HUD", "Potion Effects", "Toggle Sprint", "Zoom", "Crosshair", "Freelook")),
            new BuiltIn("Clean / Streamer", "Almost no HUD, no coords or server IP", Set.of(
                    "FPS", "Potion Effects", "Toggle Sprint", "Zoom", "Crosshair", "Freelook", "Hide Fire Overlay")),
            new BuiltIn("Everything", "Turn every feature on", Set.of("*")));

    public static void apply(BuiltIn p) {
        boolean all = p.enabled().contains("*");
        for (Module m : ModuleManager.all()) {
            if (KEEP.contains(m.name())) continue;
            if (all && (m.name().equals("Hide Boss Bar") || m.name().equals("Hide Scoreboard"))) continue;
            m.setEnabled(all || p.enabled().contains(m.name()));
        }
        Config.save();
    }

    // ---------- custom slots ----------
    public static final int SLOTS = 3;
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("therabbits/presets");

    private static Path slot(int i) { return DIR.resolve("custom_" + (i + 1) + ".json"); }

    public static boolean hasSlot(int i) { return Files.exists(slot(i)); }

    public static String slotInfo(int i) {
        try {
            if (!hasSlot(i)) return "Empty";
            FileTime t = Files.getLastModifiedTime(slot(i));
            return "Saved " + DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(t.toInstant());
        } catch (Exception e) { return "Saved"; }
    }

    public static void saveSlot(int i) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(slot(i), Config.gson().toJson(Config.toJson()));
        } catch (Exception e) {
            RabbitsClient.LOG.error("Could not save preset", e);
        }
    }

    public static void loadSlot(int i) {
        try {
            if (!hasSlot(i)) return;
            JsonObject o = JsonParser.parseString(Files.readString(slot(i))).getAsJsonObject();
            Config.apply(o);
            Config.save();
        } catch (Exception e) {
            RabbitsClient.LOG.error("Could not load preset", e);
        }
    }

    /** How many features a built-in preset turns on (for the card). */
    public static int count(BuiltIn p) {
        if (p.enabled().contains("*")) return (int) ModuleManager.all().stream().filter(m -> !KEEP.contains(m.name())).count();
        return (int) ModuleManager.all().stream().filter(m -> p.enabled().contains(m.name())).count();
    }

    /** True when the current toggles match this built-in preset. */
    public static boolean active(BuiltIn p) {
        boolean all = p.enabled().contains("*");
        for (Module m : ModuleManager.all()) {
            if (KEEP.contains(m.name())) continue;
            if (all && (m.name().equals("Hide Boss Bar") || m.name().equals("Hide Scoreboard"))) continue;
            if (m.enabled() != (all || p.enabled().contains(m.name()))) return false;
        }
        return true;
    }
}
