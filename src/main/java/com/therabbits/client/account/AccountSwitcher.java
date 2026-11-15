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
    /** Extra premium (Microsoft) accounts: name, uuid, refresh token. Stored only on this computer. */
    public record Premium(String name, String uuid, String refresh) {}
    private static final List<Premium> PREMIUM = new ArrayList<>();
    private static boolean loaded;
    /** Account picked last time ("main", "premium:<uuid>" or "offline:<name>"), restored at startup. */
    private static String last = "main";
    public static final java.util.concurrent.atomic.AtomicBoolean BUSY = new java.util.concurrent.atomic.AtomicBoolean();

    private static void load() {
        if (loaded) return;
        loaded = true;
        main = Minecraft.getInstance().getUser();
        try {
            if (Files.exists(FILE)) {
                JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                if (o.has("last")) last = o.get("last").getAsString();
                if (o.has("offline")) for (var e : o.getAsJsonArray("offline")) OFFLINE.add(e.getAsString());
                if (o.has("premium")) for (var e : o.getAsJsonArray("premium")) {
                    JsonObject p = e.getAsJsonObject();
                    PREMIUM.add(new Premium(p.get("name").getAsString(), p.get("uuid").getAsString(), p.get("refresh").getAsString()));
                }
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
            JsonArray pa = new JsonArray();
            for (Premium p : PREMIUM) {
                JsonObject po = new JsonObject();
                po.addProperty("name", p.name()); po.addProperty("uuid", p.uuid()); po.addProperty("refresh", p.refresh());
                pa.add(po);
            }
            o.add("premium", pa);
            o.addProperty("last", last);
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

    public static void useMain() { load(); set(main); remember("main"); }

    private static void remember(String key) { last = key; save(); }

    /** Called once when the game starts: switch back to the account you used last time. */
    public static void restoreLast() {
        load();
        try {
            if (last.startsWith("offline:")) {
                String n = last.substring(8);
                if (OFFLINE.contains(n)) useOffline(n);
            } else if (last.startsWith("premium:")) {
                String id = last.substring(8);
                for (Premium p : PREMIUM) if (p.uuid().equals(id)) { usePremium(p); break; }
            }
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Could not restore last account", e);
        }
    }

    /** Short reason a cracked name can't be used, or null if it's fine. */
    public static String nameProblem(String n) {
        n = n == null ? "" : n.trim();
        if (n.isEmpty()) return null;
        if (n.length() < 3) return "At least 3 characters";
        if (n.length() > 16) return "16 characters max";
        if (!n.matches("[A-Za-z0-9_]+")) return "Only letters, numbers and _";
        load();
        for (String o : OFFLINE) if (o.equalsIgnoreCase(n)) return "You already have that name";
        return null;
    }

    public static List<Premium> premium() { load(); return List.copyOf(PREMIUM); }

    public static boolean isCurrentPremium(Premium p) {
        User u = Minecraft.getInstance().getUser();
        return u != main && u.getProfileId().toString().equals(p.uuid());
    }

    /** Saves (or updates) a premium account after signing in. */
    public static void addPremium(MicrosoftAuth.Result r) {
        load();
        PREMIUM.removeIf(p -> p.uuid().equals(r.uuid().toString()));
        PREMIUM.add(new Premium(r.name(), r.uuid().toString(), r.refreshToken()));
        save();
    }

    public static void removePremium(Premium p) {
        load();
        if (isCurrentPremium(p)) useMain();
        PREMIUM.removeIf(q -> q.uuid().equals(p.uuid()));
        save();
    }

    /** Signs in with a saved premium account (on a background thread) and switches to it. */
    public static java.util.concurrent.CompletableFuture<String> usePremium(Premium p) {
        if (!BUSY.compareAndSet(false, true)) return java.util.concurrent.CompletableFuture.completedFuture("Already signing in...");
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                MicrosoftAuth.Result r = MicrosoftAuth.refresh(p.refresh());
                addPremium(r);   // keep the newest refresh token
                Minecraft.getInstance().execute(() -> playAs(r));
                return "Now playing as " + r.name();
            } catch (MicrosoftAuth.AuthException e) {
                return e.getMessage();
            } catch (Exception e) {
                RabbitsClient.LOG.warn("Premium sign-in failed", e);
                return "Couldn't sign in - check your internet";
            } finally {
                BUSY.set(false);
            }
        });
    }

    public static void playAs(MicrosoftAuth.Result r) {
        set(new User(r.name(), r.uuid(), r.mcToken(), Optional.ofNullable(r.xuid()), Optional.empty()));
        remember("premium:" + r.uuid());
    }

    public static void useOffline(String name) {
        load();
        set(new User(name, UUIDUtil.createOfflinePlayerUUID(name), "0", Optional.empty(), Optional.empty()));
        remember("offline:" + name);
    }

    private static void set(User u) {
        ((MinecraftAccessor) Minecraft.getInstance()).therabbits$setUser(u);
        RabbitsClient.LOG.info("Now playing as {}", u.getName());
    }
}
