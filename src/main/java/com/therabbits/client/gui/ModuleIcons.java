package com.therabbits.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.therabbits.client.RabbitsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A picture for every feature: a fitting Minecraft item, or your own PNG from
 * .minecraft/config/therabbits/icons/&lt;Feature Name&gt;.png
 */
public final class ModuleIcons {
    private ModuleIcons() {}

    private static final Map<String, Item> ITEMS = new HashMap<>();
    static {
        ITEMS.put("HUD Style", Items.PAINTING);
        ITEMS.put("FPS", Items.CLOCK);
        ITEMS.put("Ping", Items.REPEATER);
        ITEMS.put("Coordinates", Items.MAP);
        ITEMS.put("Direction", Items.COMPASS);
        ITEMS.put("Keystrokes", Items.STONE_BUTTON);
        ITEMS.put("Armor HUD", Items.DIAMOND_CHESTPLATE);
        ITEMS.put("Potion Effects", Items.POTION);
        ITEMS.put("Saturation", Items.COOKED_BEEF);
        ITEMS.put("Clock", Items.CLOCK);
        ITEMS.put("Server IP", Items.NAME_TAG);
        ITEMS.put("Memory", Items.REDSTONE);
        ITEMS.put("Speed", Items.FEATHER);
        ITEMS.put("Biome", Items.OAK_SAPLING);
        ITEMS.put("Look Angle", Items.ARROW);
        ITEMS.put("Session Time", Items.BELL);
        ITEMS.put("Item Counter", Items.CHEST);
        ITEMS.put("Toggle Sprint", Items.LEATHER_BOOTS);
        ITEMS.put("Zoom", Items.SPYGLASS);
        ITEMS.put("Crosshair", Items.TARGET);
        ITEMS.put("Reach Display", Items.BOW);
        ITEMS.put("Totem Pops", Items.TOTEM_OF_UNDYING);
        ITEMS.put("CPS", Items.LEVER);
        ITEMS.put("Combo Counter", Items.IRON_SWORD);
        ITEMS.put("Fullbright", Items.GLOWSTONE_DUST);
        ITEMS.put("No Hurt Cam", Items.SHIELD);
        ITEMS.put("Hide Scoreboard", Items.OAK_SIGN);
        ITEMS.put("Menu Background", Items.ITEM_FRAME);
        ITEMS.put("Hide Fire Overlay", Items.FIRE_CHARGE);
        ITEMS.put("No Totem Animation", Items.ARMOR_STAND);
        ITEMS.put("Hide Boss Bar", Items.WITHER_SKELETON_SKULL);
        ITEMS.put("Hide Vignette", Items.TINTED_GLASS);
        ITEMS.put("Low Health Warning", Items.GLISTERING_MELON_SLICE);
        ITEMS.put("Freelook", Items.ENDER_EYE);
        ITEMS.put("Custom Text", Items.WRITABLE_BOOK);
        ITEMS.put("Inventory HUD", Items.BARREL);
        ITEMS.put("Waypoints", Items.LODESTONE);
        ITEMS.put("Cooldowns", Items.ENDER_PEARL);
        ITEMS.put("Block Outline", Items.GLASS);
        ITEMS.put("No Fog", Items.POWDER_SNOW_BUCKET);
        ITEMS.put("Enchant Glint", Items.ENCHANTED_BOOK);
        ITEMS.put("Item Physics", Items.DIAMOND);
        ITEMS.put("ViewModel", Items.DIAMOND_PICKAXE);
        ITEMS.put("Title Tweaker", Items.OAK_HANGING_SIGN);
        ITEMS.put("Hitboxes", Items.SCAFFOLDING);
        ITEMS.put("Mouse Tweaks", Items.HOPPER);
        ITEMS.put("Alt Hider", Items.CARVED_PUMPKIN);
    }

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("therabbits/icons");
    private record Custom(Identifier id, int w, int h) {}
    private static final Map<String, Optional<Custom>> CUSTOM = new HashMap<>();
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    /** Forget loaded custom pictures so new files are picked up (called when the menu opens). */
    public static void reload() {
        CUSTOM.clear();
        try { Files.createDirectories(DIR); } catch (Exception ignored) {}
    }

    private static Optional<Custom> custom(String name) {
        return CUSTOM.computeIfAbsent(name, n -> {
            Path file = DIR.resolve(n + ".png");
            if (!Files.exists(file)) return Optional.empty();
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage img = NativeImage.read(in);
                String key = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                Identifier id = Identifier.fromNamespaceAndPath("therabbits", "icons/" + key);
                int w = img.getWidth(), h = img.getHeight();
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "therabbits icon " + key, img));
                return Optional.of(new Custom(id, w, h));
            } catch (Exception e) {
                RabbitsClient.LOG.warn("Could not load icon {}", file, e);
                return Optional.empty();
            }
        });
    }

    /** Draws the feature's picture into a size x size square. */
    public static void draw(GuiGraphics g, String name, int x, int y, int size) {
        Optional<Custom> c = custom(name);
        if (c.isPresent()) {
            Custom ci = c.get();
            g.blit(RenderPipelines.GUI_TEXTURED, ci.id(), x, y, 0, 0, size, size, ci.w(), ci.h(), ci.w(), ci.h());
            return;
        }
        ItemStack stack = STACKS.computeIfAbsent(name, n -> new ItemStack(ITEMS.getOrDefault(n, Items.PAPER)));
        if (size == 16) {
            g.renderItem(stack, x, y);
        } else {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(size / 16f, size / 16f);
            g.renderItem(stack, 0, 0);
            g.pose().popMatrix();
        }
    }
}
