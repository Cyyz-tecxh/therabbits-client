package com.therabbits.client.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.therabbits.client.RabbitsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pack mixer: pick, per item (totem, pearl, sword ...), which of your resource packs it comes from.
 * Your picks are copied into one generated pack, "TheRabbits Mix", which sits on top of your other packs.
 */
public final class PackMixer {
    private PackMixer() {}

    public static final String MIX_NAME = "TheRabbits Mix";
    private static final Path CHOICES = FabricLoader.getInstance().getConfigDir().resolve("therabbits/pack_mix.json");
    private static final String ITEM_DIRS = "(textures/item|textures/block|models/item|models/block|items)/";

    /** One thing you can pick a pack for. Paths are relative to assets/minecraft/. */
    public record Category(String name, Item icon, Pattern match) {
        boolean matches(String path) { return match.matcher(path).matches(); }
    }

    private static Category cat(String name, Item icon, String basenameRegex, String... prefixes) {
        StringBuilder re = new StringBuilder("(?:" + ITEM_DIRS + "(?:" + basenameRegex + ")\\.(?:png|png\\.mcmeta|json)");
        for (String p : prefixes) re.append("|").append(Pattern.quote(p)).append(".*");
        re.append(")");
        return new Category(name, icon, Pattern.compile(re.toString()));
    }

    public static final List<Category> CATEGORIES = List.of(
            cat("Totem", Items.TOTEM_OF_UNDYING, "totem_of_undying"),
            cat("Pearl", Items.ENDER_PEARL, "ender_pearl"),
            cat("Gapple", Items.GOLDEN_APPLE, "(enchanted_)?golden_apple"),
            cat("Crystal", Items.END_CRYSTAL, "end_crystal", "textures/entity/end_crystal/"),
            cat("Anchor", Items.RESPAWN_ANCHOR, "respawn_anchor.*"),
            cat("Obsidian", Items.OBSIDIAN, "(crying_)?obsidian"),
            cat("Sword", Items.DIAMOND_SWORD, ".*_sword"),
            cat("Axe", Items.DIAMOND_AXE, ".*(?<!pick)_axe"),
            cat("Mace", Items.MACE, "mace.*"),
            cat("Spear", Items.TRIDENT, ".*_spear.*"),
            cat("Armor", Items.DIAMOND_CHESTPLATE, ".*_(helmet|chestplate|leggings|boots)",
                    "textures/entity/equipment/humanoid/", "textures/entity/equipment/humanoid_leggings/"),
            cat("Elytra", Items.ELYTRA, "elytra.*", "textures/entity/equipment/wings/"),
            cat("Shield", Items.SHIELD, "shield.*", "textures/entity/shield"),
            cat("Bow", Items.BOW, "(bow|crossbow)(_.*)?", "textures/entity/projectiles/arrow"),
            cat("Trident", Items.TRIDENT, "trident.*", "textures/entity/trident"),
            cat("Pickaxe", Items.DIAMOND_PICKAXE, ".*_pickaxe"),
            cat("Shovel", Items.DIAMOND_SHOVEL, ".*_shovel"),
            cat("Hoe", Items.DIAMOND_HOE, ".*_hoe"),
            cat("Potions", Items.SPLASH_POTION, "(splash_|lingering_)?potion.*"),
            cat("Food", Items.COOKED_BEEF, "(cooked_.*|golden_carrot|bread|baked_potato)"),
            cat("Glint", Items.ENCHANTED_BOOK, "$^", "textures/misc/enchanted_glint"),
            cat("Fire", Items.FLINT_AND_STEEL, "$^", "textures/block/fire_", "textures/block/soul_fire"),
            cat("Ender Chest", Items.ENDER_CHEST, "ender_chest", "textures/entity/chest/ender"),
            cat("Shulker", Items.SHULKER_BOX, ".*shulker_box", "textures/entity/shulker/"),
            cat("TNT", Items.TNT, "tnt.*"),
            cat("Bedrock", Items.BEDROCK, "bedrock"),
            cat("Blocks", Items.COBBLESTONE, "(cobblestone|stone|oak_planks|end_stone|dirt|grass_block.*|deepslate)"),
            cat("Hearts & Hotbar", Items.RED_DYE, "$^", "textures/gui/sprites/hud/heart", "textures/gui/sprites/hud/hotbar",
                    "textures/gui/sprites/hud/food", "textures/gui/sprites/hud/armor", "textures/gui/sprites/hud/experience"),
            cat("Crosshair", Items.TARGET, "$^", "textures/gui/sprites/hud/crosshair"),
            cat("Sky", Items.LIGHT_BLUE_STAINED_GLASS, "$^", "textures/environment/"),
            cat("Particles", Items.BLAZE_POWDER, "$^", "textures/particle/")
    );

    /** A resource pack in your resourcepacks folder (zip or folder). */
    public static final class Source {
        public final String file;       // file/folder name, e.g. "Cool Pack.zip"
        public final Path path;
        final Map<String, String> entries = new HashMap<>();   // assets/minecraft/... path -> path inside pack (handles a nested root)
        Source(String file, Path path) { this.file = file; this.path = path; }
        public String displayName() { return file.replaceAll("(?i)\\.zip$", ""); }

        public byte[] read(String assetPath) throws Exception {
            String inner = entries.get(assetPath);
            if (inner == null) return null;
            if (Files.isDirectory(path)) return Files.readAllBytes(path.resolve(inner));
            try (ZipFile z = new ZipFile(path.toFile())) {
                ZipEntry e = z.getEntry(inner);
                if (e == null) return null;
                try (InputStream in = z.getInputStream(e)) { return in.readAllBytes(); }
            }
        }

        public boolean has(Category c) {
            for (String p : entries.keySet()) if (c.matches(p)) return true;
            return false;
        }

        /** A picture to show for this pack in this category (first matching texture). */
        public String previewPath(Category c) {
            String best = null;
            for (String p : entries.keySet()) {
                if (!p.endsWith(".png") || !c.matches(p)) continue;
                if (best == null || (p.contains("textures/item/") && !best.contains("textures/item/")) || p.length() < best.length()) best = p;
            }
            return best;
        }
    }

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final Map<String, String> CHOICE = new LinkedHashMap<>();   // category -> pack file name
    private static boolean loaded;

    public static Path packDir() { return Minecraft.getInstance().getResourcePackDirectory(); }

    /** Re-reads your resourcepacks folder. */
    public static synchronized void scan() {
        loadChoices();
        SOURCES.clear();
        try (Stream<Path> files = Files.list(packDir())) {
            for (Path p : files.sorted().toList()) {
                String name = p.getFileName().toString();
                if (name.equals(MIX_NAME)) continue;
                boolean zip = name.toLowerCase(Locale.ROOT).endsWith(".zip");
                if (!zip && !Files.isDirectory(p)) continue;
                Source s = new Source(name, p);
                try {
                    if (zip) {
                        try (ZipFile z = new ZipFile(p.toFile())) {
                            z.stream().forEach(e -> index(s, e.getName()));
                        }
                    } else {
                        try (Stream<Path> walk = Files.walk(p, 12)) {
                            walk.filter(Files::isRegularFile).forEach(f -> index(s, p.relativize(f).toString().replace('\\', '/')));
                        }
                    }
                } catch (Exception e) {
                    RabbitsClient.LOG.warn("Pack mixer: couldn't read {}", name, e);
                }
                if (!s.entries.isEmpty()) SOURCES.add(s);
            }
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Pack mixer: couldn't list resource packs", e);
        }
    }

    private static void index(Source s, String inner) {
        int i = inner.indexOf("assets/minecraft/");
        if (i < 0 || inner.endsWith("/")) return;
        s.entries.putIfAbsent(inner.substring(i + "assets/minecraft/".length()), inner);
    }

    public static synchronized List<Source> sources() { return Collections.unmodifiableList(SOURCES); }

    public static synchronized List<Source> sourcesFor(Category c) {
        List<Source> out = new ArrayList<>();
        for (Source s : SOURCES) if (s.has(c)) out.add(s);
        return out;
    }

    public static synchronized String choice(Category c) { loadChoices(); return CHOICE.get(c.name()); }

    public static synchronized int changedCount() { loadChoices(); return CHOICE.size(); }

    public static synchronized void choose(Category c, String packFile) {
        loadChoices();
        if (packFile == null) CHOICE.remove(c.name()); else CHOICE.put(c.name(), packFile);
        saveChoices();
    }

    public static synchronized void resetAll() { loadChoices(); CHOICE.clear(); saveChoices(); }

    private static void loadChoices() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(CHOICES)) {
                JsonObject o = JsonParser.parseString(Files.readString(CHOICES)).getAsJsonObject();
                for (var e : o.entrySet()) CHOICE.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Pack mixer: couldn't read choices", e);
        }
    }

    private static void saveChoices() {
        try {
            JsonObject o = new JsonObject();
            CHOICE.forEach(o::addProperty);
            Files.createDirectories(CHOICES.getParent());
            Files.writeString(CHOICES, o.toString());
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Pack mixer: couldn't save choices", e);
        }
    }

    /** Builds the "TheRabbits Mix" pack from your picks. Returns how many files were copied. */
    public static synchronized int build() throws Exception {
        loadChoices();
        Path out = packDir().resolve(MIX_NAME);
        if (Files.exists(out)) {
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
        Files.createDirectories(out);
        Files.writeString(out.resolve("pack.mcmeta"), """
                {"pack": {"description": "Made by TheRabbits Pack Customizer", "pack_format": 75,
                  "supported_formats": [1, 999], "min_format": 1, "max_format": 999}}""");
        int copied = 0;
        for (Category c : CATEGORIES) {
            String file = CHOICE.get(c.name());
            if (file == null) continue;
            Source s = SOURCES.stream().filter(x -> x.file.equals(file)).findFirst().orElse(null);
            if (s == null) continue;
            Set<String> todo = new HashSet<>();
            for (String p : s.entries.keySet()) if (c.matches(p)) todo.add(p);
            // also bring models/textures those files point at, so 3D items keep working
            Set<String> done = new HashSet<>();
            List<String> queue = new ArrayList<>(todo);
            while (!queue.isEmpty()) {
                String p = queue.remove(queue.size() - 1);
                if (!done.add(p)) continue;
                byte[] data = s.read(p);
                if (data == null) continue;
                Path dst = out.resolve("assets/minecraft/" + p);
                Files.createDirectories(dst.getParent());
                Files.write(dst, data);
                copied++;
                if (p.endsWith(".png") && s.entries.containsKey(p + ".mcmeta")) queue.add(p + ".mcmeta");
                if (p.endsWith(".json")) for (String ref : references(new String(data, java.nio.charset.StandardCharsets.UTF_8))) {
                    for (String cand : List.of("models/" + ref + ".json", "textures/" + ref + ".png"))
                        if (s.entries.containsKey(cand) && !done.contains(cand)) queue.add(cand);
                }
            }
        }
        return copied;
    }

    /** Resource ids mentioned in a model / item json ("minecraft:item/x", "item/x", "block/x" ...). */
    private static Set<String> references(String json) {
        Set<String> out = new HashSet<>();
        try {
            collect(JsonParser.parseString(json), out);
        } catch (Exception ignored) {}
        return out;
    }

    private static void collect(JsonElement e, Set<String> out) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String v = e.getAsString();
            if (v.startsWith("minecraft:")) v = v.substring(10);
            if (v.matches("[a-z0-9_./-]+") && v.contains("/")) out.add(v);
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) collect(x, out);
        } else if (e.isJsonObject()) {
            for (var x : e.getAsJsonObject().entrySet()) collect(x.getValue(), out);
        }
    }
}
