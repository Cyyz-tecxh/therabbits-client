package com.therabbits.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.therabbits.client.RabbitsClient;
import com.therabbits.client.mixin.MinecraftAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Switch between your launcher (premium) account and offline ("cracked") names.
 * Offline names only work on offline-mode servers, LAN and singleplayer.
 */
public final class AccountSwitcher {
    private AccountSwitcher() {}

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("therabbits/accounts.json");
    private static User main;
    private static final List<String> OFFLINE = new ArrayList<>();
    private static boolean loaded;

    private static void load() {
        if (loaded) return;
        loaded = true;
        main = Minecraft.getInstance().getUser();
        try {
            if (Files.exists(FILE)) {
                JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                for (var e : o.getAsJsonArray("offline")) OFFLINE.add(e.getAsString());
            }
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Could not read accounts", e);
        }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            OFFLINE.forEach(a::add);
            o.add("offline", a);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, o.toString());
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Could not save accounts", e);
        }
    }

    public static User main() { load(); return main; }
    public static List<String> offline() { load(); return List.copyOf(OFFLINE); }
    public static boolean mainIsPremium() { load(); return main.getAccessToken() != null && main.getAccessToken().length() > 20; }

    public static boolean isCurrent(String offlineName) {
        User u = Minecraft.getInstance().getUser();
        return u != main && u.getName().equals(offlineName);
    }

    public static boolean usingMain() { load(); return Minecraft.getInstance().getUser() == main; }

    public static boolean validName(String n) { return n != null && n.matches("[A-Za-z0-9_]{3,16}"); }

    /** Returns an error message, or null when added. */
    public static String add(String name) {
        load();
        name = name == null ? "" : name.trim();
        if (!validName(name)) return "Names are 3-16 letters, numbers or _";
        for (String s : OFFLINE) if (s.equalsIgnoreCase(name)) return "You already added " + s;
        OFFLINE.add(name);
        save();
        return null;
    }

    public static void remove(String name) {
        load();
        if (isCurrent(name)) useMain();
        OFFLINE.remove(name);
        save();
    }

    public static void useMain() { load(); set(main); }

    public static void useOffline(String name) {
        load();
        set(new User(name, UUIDUtil.createOfflinePlayerUUID(name), "0", Optional.empty(), Optional.empty()));
    }

    private static void set(User u) {
        ((MinecraftAccessor) Minecraft.getInstance()).therabbits$setUser(u);
        RabbitsClient.LOG.info("Now playing as {}", u.getName());
    }
}
