package com.therabbits.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import com.therabbits.client.RabbitsClient;
import com.therabbits.client.mixin.DefaultPlayerSkinAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-side skin changer: your account skin, the vanilla default skins, or PNGs you upload.
 * Only changes how YOU see your skin; other players still see your Minecraft account skin.
 */
public final class SkinChanger {
    private record Option(String name, PlayerSkin skin) {}

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("therabbits/skins");
    private static final Path SELECTED = FabricLoader.getInstance().getConfigDir().resolve("therabbits/selected_skin.txt");
    private static final List<Option> OPTIONS = new ArrayList<>();
    private static int index = 0; // 0 = your own skin
    private static boolean loaded;

    private SkinChanger() {}

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        OPTIONS.clear();
        OPTIONS.add(new Option("Your skin", null));
        for (PlayerSkin s : DefaultPlayerSkinAccessor.therabbits$defaults()) OPTIONS.add(new Option(nameOf(s), s));
        try {
            Files.createDirectories(DIR);
            try (var files = Files.list(DIR)) {
                files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().forEach(SkinChanger::addFile);
            }
            if (Files.exists(SELECTED)) {
                String want = Files.readString(SELECTED).trim();
                for (int i = 0; i < OPTIONS.size(); i++) if (OPTIONS.get(i).name().equals(want)) index = i;
            }
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Skin changer: could not load skins", e);
        }
    }

    private static String nameOf(PlayerSkin s) {
        String p = s.body().texturePath().getPath();           // textures/entity/player/wide/steve.png
        String n = p.substring(p.lastIndexOf('/') + 1).replace(".png", "");
        String pretty = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        return pretty + (s.model() == PlayerModelType.SLIM ? " (slim)" : "");
    }

    private static Option addFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            if (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
                img.close();
                return null;
            }
            boolean slim = img.getHeight() == 64 && ((img.getPixel(54, 20) >>> 24) == 0);
            String key = file.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            Identifier id = Identifier.fromNamespaceAndPath("therabbits", "skins/" + key);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "therabbits skin " + key, img));
            ClientAsset.Texture tex = new ClientAsset.Texture() {
                @Override public Identifier id() { return id; }
                @Override public Identifier texturePath() { return id; }
            };
            String name = file.getFileName().toString().replaceAll("(?i)\\.png$", "");
            Option o = new Option(name, new PlayerSkin(tex, null, null, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, true));
            OPTIONS.add(o);
            return o;
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Skin changer: bad skin file {}", file, e);
            return null;
        }
    }

    /** The skin to show for you, or null to use your real one. */
    public static PlayerSkin override() {
        ensureLoaded();
        return OPTIONS.get(index).skin();
    }

    public static String currentName() {
        ensureLoaded();
        return OPTIONS.get(index).name();
    }

    public static void next() { ensureLoaded(); index = (index + 1) % OPTIONS.size(); save(); }
    public static void previous() { ensureLoaded(); index = (index - 1 + OPTIONS.size()) % OPTIONS.size(); save(); }
    public static void reset() { ensureLoaded(); index = 0; save(); }

    /** Opens a file picker for a 64x64 skin PNG, copies it into config/therabbits/skins and selects it. */
    public static String upload() {
        ensureLoaded();
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png")).flip();
            path = TinyFileDialogs.tinyfd_openFileDialog("Choose a Minecraft skin (64x64 PNG)", "", filters, "PNG skin", false);
        }
        if (path == null) return null;
        try {
            Path src = Path.of(path);
            Path dst = DIR.resolve(src.getFileName().toString());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            Option o = addFile(dst);
            if (o == null) return "That file isn't a 64x64 Minecraft skin";
            index = OPTIONS.indexOf(o);
            save();
            return null;
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Skin changer: upload failed", e);
            return "Couldn't load that skin";
        }
    }

    private static void save() {
        try {
            Files.createDirectories(SELECTED.getParent());
            Files.writeString(SELECTED, currentName());
        } catch (Exception ignored) {}
    }
}
