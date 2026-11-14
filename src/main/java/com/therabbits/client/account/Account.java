package com.therabbits.client.account;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.therabbits.client.RabbitsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Your TheRabbits account on the TheRabbits server: login token, unlocked cosmetics, license,
 * creator you support, friend code, friends and party. Everything here talks to the same server
 * URL as cosmetics (Cosmetics -> Capes -> Server URL).
 */
public final class Account {
    private Account() {}

    public record Person(String uuid, String name, boolean online) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("therabbits/account.json");

    // saved
    private static String token, tokenFor;          // tokenFor = Minecraft account uuid the token belongs to
    private static boolean cachedLicensed, cachedLicenseRequired;
    // live
    public static volatile String name, friendCode, creator, partyCode, partyLeader;
    public static volatile boolean licensed, licenseRequired, configLoaded;
    public static volatile Set<String> owned = Set.of(), locked = Set.of();
    public static volatile List<Person> friends = List.of(), party = List.of();
    public static volatile String status = "Not connected";
    private static long lastRefresh, lastHeartbeat;
    private static volatile boolean busy;

    static { load(); }

    public static String serverUrl() {
        String u = com.therabbits.client.module.cosmetics.AccountModule.url();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    public static boolean hasServer() { return !serverUrl().isBlank(); }

    private static String mcUuid() {
        var u = Minecraft.getInstance().getUser();
        return u == null || u.getProfileId() == null ? "" : u.getProfileId().toString();
    }

    public static boolean loggedIn() { return token != null && mcUuid().equals(tokenFor); }

    /** Cosmetic is locked for you (the server owner locked it and you haven't unlocked it with a code). */
    public static boolean isLocked(String id) {
        return id != null && !id.equals("none") && locked.contains(id) && !owned.contains(id);
    }

    // ---------------------------------------------------------------- persistence
    private static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            token = o.has("token") ? o.get("token").getAsString() : null;
            tokenFor = o.has("for") ? o.get("for").getAsString() : null;
            cachedLicensed = o.has("licensed") && o.get("licensed").getAsBoolean();
            cachedLicenseRequired = o.has("licenseRequired") && o.get("licenseRequired").getAsBoolean();
            licensed = cachedLicensed; licenseRequired = cachedLicenseRequired;
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Couldn't read account file", e);
        }
    }

    private static synchronized void save() {
        try {
            JsonObject o = new JsonObject();
            if (token != null) { o.addProperty("token", token); o.addProperty("for", tokenFor); }
            o.addProperty("licensed", licensed);
            o.addProperty("licenseRequired", licenseRequired);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, new Gson().toJson(o));
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Couldn't save account file", e);
        }
    }

    // ---------------------------------------------------------------- http
    private static HttpResponse<String> send(String method, String path, JsonObject body, boolean auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(serverUrl() + path)).timeout(Duration.ofSeconds(10));
        if (auth && token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
        else b.method(method, HttpRequest.BodyPublishers.noBody());
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject obj(HttpResponse<String> r) {
        try { return JsonParser.parseString(r.body()).getAsJsonObject(); } catch (Exception e) { return new JsonObject(); }
    }

    private static String err(HttpResponse<String> r) {
        JsonObject o = obj(r);
        return o.has("error") ? o.get("error").getAsString() : "Server error (" + r.statusCode() + ")";
    }

    private static Set<String> strings(JsonObject o, String k) {
        Set<String> out = new HashSet<>();
        if (o.has(k) && o.get(k).isJsonArray()) for (JsonElement e : o.getAsJsonArray(k)) out.add(e.getAsString());
        return out;
    }

    private static List<Person> people(JsonArray a) {
        List<Person> out = new ArrayList<>();
        if (a != null) for (JsonElement e : a) {
            JsonObject p = e.getAsJsonObject();
            out.add(new Person(p.get("uuid").getAsString(), p.get("name").getAsString(), p.get("online").getAsBoolean()));
        }
        out.sort((x, y) -> x.online() == y.online() ? x.name().compareToIgnoreCase(y.name()) : x.online() ? -1 : 1);
        return out;
    }

    // ---------------------------------------------------------------- actions
    /** Loads the public server settings (locked cosmetics, license required). */
    public static CompletableFuture<Void> loadConfig() {
        if (!hasServer()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> r = send("GET", "/config", null, false);
                if (r.statusCode() != 200) return;
                JsonObject o = obj(r);
                locked = strings(o, "locked");
                licenseRequired = o.has("licenseRequired") && o.get("licenseRequired").getAsBoolean();
                configLoaded = true;
                save();
            } catch (Exception e) {
                RabbitsClient.LOG.debug("Config load failed", e);
            }
        });
    }

    /** Proves to the server who you are (Mojang session check) and gets a token. */
    public static CompletableFuture<String> login() {
        if (!hasServer()) return CompletableFuture.completedFuture("Set the Server URL first (Account & Codes)");
        return CompletableFuture.supplyAsync(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                HttpResponse<String> ch = send("GET", "/challenge", null, false);
                if (ch.statusCode() != 200) return err(ch);
                String serverId = obj(ch).get("serverId").getAsString();
                mc.services().sessionService().joinServer(mc.getUser().getProfileId(), mc.getUser().getAccessToken(), serverId);
                JsonObject body = new JsonObject();
                body.addProperty("name", mc.getUser().getName());
                body.addProperty("serverId", serverId);
                HttpResponse<String> r = send("POST", "/login", body, false);
                if (r.statusCode() != 200) return r.statusCode() == 403 ? "Login failed (premium account needed)" : err(r);
                token = obj(r).get("token").getAsString();
                tokenFor = mcUuid();
                save();
                refreshNow();
                return "Connected";
            } catch (Exception e) {
                RabbitsClient.LOG.warn("Login failed", e);
                return "Couldn't connect (premium account needed)";
            }
        });
    }

    private static CompletableFuture<String> ensureLogin() {
        return loggedIn() ? CompletableFuture.completedFuture("Connected") : login();
    }

    private static CompletableFuture<String> action(String path, JsonObject body, String okMessage) {
        return ensureLogin().thenApplyAsync(s -> {
            if (!loggedIn()) return s;
            try {
                HttpResponse<String> r = send("POST", path, body, true);
                if (r.statusCode() == 401) { token = null; save(); return "Please connect again"; }
                if (r.statusCode() != 200) return err(r);
                JsonObject o = obj(r);
                refreshNow();
                return o.has("message") ? o.get("message").getAsString() : okMessage;
            } catch (Exception e) {
                return "Couldn't reach the server";
            }
        });
    }

    private static JsonObject one(String k, String v) { JsonObject o = new JsonObject(); o.addProperty(k, v); return o; }

    public static CompletableFuture<String> redeem(String code) { return action("/redeem", one("code", code.trim()), "Code redeemed!"); }
    public static CompletableFuture<String> addFriend(String code) { return action("/friends/add", one("code", code.trim()), "Friend added"); }
    public static CompletableFuture<String> removeFriend(String uuid) { return action("/friends/remove", one("uuid", uuid), "Removed"); }
    public static CompletableFuture<String> createParty() { return action("/party/create", null, "Party created"); }
    public static CompletableFuture<String> joinParty(String code) { return action("/party/join", one("code", code.trim()), "Joined the party"); }
    public static CompletableFuture<String> leaveParty() { return action("/party/leave", null, "Left the party"); }

    /** Reloads everything about your account (unlocks, license, friends, party). */
    public static void refreshNow() {
        if (!loggedIn() || busy) return;
        busy = true;
        lastRefresh = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> r = send("GET", "/me", null, true);
                if (r.statusCode() == 401) { token = null; status = "Please connect again"; save(); return; }
                if (r.statusCode() != 200) { status = err(r); return; }
                JsonObject me = obj(r);
                name = me.get("name").getAsString();
                owned = strings(me, "owned");
                locked = strings(me, "locked");
                licensed = me.get("licensed").getAsBoolean();
                licenseRequired = me.get("licenseRequired").getAsBoolean();
                creator = me.has("creator") && !me.get("creator").isJsonNull() ? me.get("creator").getAsString() : null;
                friendCode = me.has("friendCode") && !me.get("friendCode").isJsonNull() ? me.get("friendCode").getAsString() : null;
                configLoaded = true;
                JsonObject f = obj(send("GET", "/friends", null, true));
                friends = people(f.has("friends") ? f.getAsJsonArray("friends") : null);
                JsonObject p = obj(send("GET", "/party", null, true));
                if (p.has("party") && p.get("party").isJsonObject()) {
                    JsonObject pp = p.getAsJsonObject("party");
                    partyCode = pp.get("code").getAsString();
                    partyLeader = pp.get("leader").getAsString();
                    party = people(pp.getAsJsonArray("members"));
                } else { partyCode = null; party = List.of(); }
                status = "Connected as " + name;
                save();
            } catch (Exception e) {
                status = "Couldn't reach the server";
            } finally {
                busy = false;
            }
        });
    }

    /** Called every client tick: keeps you marked online and the lists fresh. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (!hasServer() || !loggedIn()) return;
        if (now - lastHeartbeat > 60_000) {
            lastHeartbeat = now;
            CompletableFuture.runAsync(() -> { try { send("POST", "/heartbeat", null, true); } catch (Exception ignored) {} });
        }
        if (now - lastRefresh > 45_000) refreshNow();
    }

    public static String friendsLine() {
        if (friends.isEmpty()) return loggedIn() ? "No friends yet" : "-";
        long on = friends.stream().filter(Person::online).count();
        StringBuilder sb = new StringBuilder(on + "/" + friends.size() + " online: ");
        for (int i = 0; i < friends.size(); i++) {
            Person p = friends.get(i);
            if (i > 0) sb.append(", ");
            sb.append(p.online() ? "§a" : "§7").append(p.name()).append("§r");
        }
        return sb.toString();
    }

    public static String partyLine() {
        if (partyCode == null) return loggedIn() ? "Not in a party" : "-";
        StringBuilder sb = new StringBuilder(partyCode + "  ");
        for (int i = 0; i < party.size(); i++) {
            Person p = party.get(i);
            if (i > 0) sb.append(", ");
            sb.append(p.uuid().equals(partyLeader) ? "★" : "").append(p.online() ? "§a" : "§7").append(p.name()).append("§r");
        }
        return sb.toString();
    }
}
