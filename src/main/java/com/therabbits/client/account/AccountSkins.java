package com.therabbits.client.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.therabbits.client.RabbitsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real skins for the accounts list. Premium accounts load by UUID; cracked names try the Mojang
 * account with the same name (like most servers would show). Falls back to the default skin.
 */
public final class AccountSkins {
    private AccountSkins() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String, PlayerSkin> SKINS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> STARTED = new ConcurrentHashMap<>();

    /** Skin for an account (default skin until the real one has loaded). */
    public static PlayerSkin get(String name, UUID uuid) {
        String key = (uuid != null ? uuid.toString() : "name:" + name).toLowerCase(Locale.ROOT);
        PlayerSkin s = SKINS.get(key);
        if (s != null) return s;
        if (STARTED.putIfAbsent(key, true) == null) {
            Thread t = new Thread(() -> load(key, name, uuid), "TheRabbits-SkinLoad");
            t.setDaemon(true);
            t.start();
        }
        return DefaultPlayerSkin.get(uuid != null ? uuid : net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name));
    }

    private static String fetch(String url) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return r.statusCode() == 200 ? r.body() : null;
    }

    private static void load(String key, String name, UUID uuid) {
        try {
            String id = uuid == null ? null : uuid.toString().replace("-", "");
            if (id == null) {   // cracked name: look up a real account with that name
                String body = fetch("https://api.mojang.com/users/profiles/minecraft/" + name);
                if (body == null) return;
                id = JsonParser.parseString(body).getAsJsonObject().get("id").getAsString();
            }
            String prof = fetch("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
            if (prof == null) return;
            String value = null;
            for (var p : JsonParser.parseString(prof).getAsJsonObject().getAsJsonArray("properties"))
                if (p.getAsJsonObject().get("name").getAsString().equals("textures")) value = p.getAsJsonObject().get("value").getAsString();
            if (value == null) return;
            JsonObject tex = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("textures");
            if (tex == null || !tex.has("SKIN")) return;
            JsonObject skin = tex.getAsJsonObject("SKIN");
            String url = skin.get("url").getAsString().replace("http://", "https://");
            boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
            byte[] png = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).body();
            Minecraft.getInstance().execute(() -> {
                try {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
                    Identifier tid = Identifier.fromNamespaceAndPath("therabbits", "account_skins/" + key.replaceAll("[^a-z0-9_.-]", "_"));
                    Minecraft.getInstance().getTextureManager().register(tid, new DynamicTexture(() -> "therabbits account skin " + key, img));
                    ClientAsset.Texture t = new ClientAsset.Texture() {
                        @Override public Identifier id() { return tid; }
                        @Override public Identifier texturePath() { return tid; }
                    };
                    SKINS.put(key, new PlayerSkin(t, null, null, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, true));
                } catch (Exception e) {
                    RabbitsClient.LOG.debug("Account skin decode failed for {}", name, e);
                }
            });
        } catch (Exception e) {
            RabbitsClient.LOG.debug("Account skin lookup failed for {}", name, e);
        }
    }
}
