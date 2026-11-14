package com.therabbits.client.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.therabbits.client.RabbitsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to the TheRabbits cosmetics server:
 *  GET  /cosmetics?uuids=a,b,c  -> {"a":{"cape":"pulse","hat":"crown","arms":"none"}, ...}
 *  GET  /challenge              -> {"serverId":"..."}
 *  POST /cape {name, serverId, cape, hat, arms}  (server checks Mojang hasJoined, so only you can set your cape)
 */
public final class CosmeticsSync {
    private CosmeticsSync() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Map<UUID, String> CAPES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> HATS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> ARMS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SHIELDS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> AURAS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FETCHED = new ConcurrentHashMap<>();
    private static final long REFRESH_MS = 5 * 60_000;
    private static long lastBatch;
    private static volatile boolean busy;

    /** Cape id another player has on the server, or null if unknown/none. */
    public static String capeOf(UUID id) {
        String c = CAPES.get(id);
        return c == null || c.equals("none") ? null : c;
    }

    public static String hatOf(UUID id) {
        String c = HATS.get(id);
        return c == null || c.equals("none") ? null : c;
    }

    public static String auraOf(UUID id) {
        String c = AURAS.get(id);
        return c == null || c.equals("none") ? null : c;
    }

    public static String shieldOf(UUID id) {
        String c = SHIELDS.get(id);
        return c == null || c.equals("none") ? null : c;
    }

    public static String armsOf(UUID id) {
        String c = ARMS.get(id);
        return c == null || c.equals("none") ? null : c;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "none";
    }

    private static String base(String url) {
        url = url.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    /** Every couple of seconds, look up capes for players we haven't asked about recently. */
    public static void tick(String serverUrl) {
        Minecraft mc = Minecraft.getInstance();
        if (serverUrl == null || serverUrl.isBlank() || mc.level == null || busy) return;
        long now = System.currentTimeMillis();
        if (now - lastBatch < 2000) return;
        lastBatch = now;
        List<UUID> want = new ArrayList<>();
        for (Player p : mc.level.players()) {
            Long t = FETCHED.get(p.getUUID());
            if (t == null || now - t > REFRESH_MS) want.add(p.getUUID());
            if (want.size() >= 60) break;
        }
        if (want.isEmpty()) return;
        for (UUID u : want) FETCHED.put(u, now);
        StringBuilder q = new StringBuilder();
        for (UUID u : want) { if (q.length() > 0) q.append(','); q.append(u.toString().replace("-", "")); }
        busy = true;
        HttpRequest req = HttpRequest.newBuilder(URI.create(base(serverUrl) + "/cosmetics?uuids=" + URLEncoder.encode(q.toString(), StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            busy = false;
            if (err != null || res.statusCode() != 200) return;
            try {
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                for (UUID u : want) {
                    String key = u.toString().replace("-", "");
                    JsonObject c = o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : new JsonObject();
                    String cape = str(c, "cape"), hat = str(c, "hat"), arms = str(c, "arms");
                    CAPES.put(u, Capes.valid(cape) ? cape : "none");
                    HATS.put(u, Wearables.validHat(hat) ? hat : "none");
                    ARMS.put(u, Wearables.validArms(arms) ? arms : "none");
                    String sh = str(c, "shield");
                    SHIELDS.put(u, Wearables.validShield(sh) ? sh : "none");
                    String au = str(c, "aura");
                    AURAS.put(u, Wearables.validAura(au) ? au : "none");
                }
            } catch (Exception e) {
                RabbitsClient.LOG.debug("Cosmetics lookup parse failed", e);
            }
        });
    }

    /** Publish your cape choice. Completes with a short status message for the menu. */
    public static CompletableFuture<String> publish(String serverUrl, String cape, String hat, String arms, String shield, String aura) {
        if (serverUrl == null || serverUrl.isBlank()) return CompletableFuture.completedFuture("Set the Server URL first (Account & Codes)");
        return CompletableFuture.supplyAsync(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                String b = base(serverUrl);
                HttpResponse<String> ch = HTTP.send(HttpRequest.newBuilder(URI.create(b + "/challenge")).timeout(Duration.ofSeconds(8)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (ch.statusCode() != 200) return "Server error (" + ch.statusCode() + ")";
                String serverId = JsonParser.parseString(ch.body()).getAsJsonObject().get("serverId").getAsString();
                // Proves to Mojang that we own this account; the server then checks it with hasJoined.
                mc.services().sessionService().joinServer(mc.getUser().getProfileId(), mc.getUser().getAccessToken(), serverId);
                JsonObject body = new JsonObject();
                body.addProperty("name", mc.getUser().getName());
                body.addProperty("serverId", serverId);
                body.addProperty("cape", cape);
                body.addProperty("hat", hat);
                body.addProperty("arms", arms);
                body.addProperty("shield", shield);
                body.addProperty("aura", aura);
                HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(b + "/cape")).timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    if (mc.player != null) {
                        CAPES.put(mc.player.getUUID(), cape);
                        HATS.put(mc.player.getUUID(), hat);
                        ARMS.put(mc.player.getUUID(), arms);
                        SHIELDS.put(mc.player.getUUID(), shield);
                        AURAS.put(mc.player.getUUID(), aura);
                    }
                    return "Synced!";
                }
                return res.statusCode() == 403 ? "Login check failed" : "Server error (" + res.statusCode() + ")";
            } catch (Exception e) {
                RabbitsClient.LOG.warn("Cape sync failed", e);
                return "Couldn't reach server";
            }
        });
    }
}
