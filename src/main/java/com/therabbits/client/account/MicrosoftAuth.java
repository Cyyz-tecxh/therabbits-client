package com.therabbits.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Microsoft (premium) sign-in with a device code: the player opens microsoft.com/link in their browser,
 * types the short code and signs in there, so the client never sees their password.
 * Chain: Microsoft token -> Xbox Live -> XSTS -> Minecraft services token -> Minecraft profile.
 */
public final class MicrosoftAuth {
    private MicrosoftAuth() {}

    private static final String CLIENT_ID = "00000000402b5328";   // Microsoft's public Minecraft client
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public record DeviceCode(String userCode, String deviceCode, String url, int interval, long expiresAt) {}
    public record Result(String name, UUID uuid, String mcToken, String refreshToken, String xuid) {}

    /** Thrown with a short message suitable for showing to the player. */
    public static final class AuthException extends Exception {
        public AuthException(String msg) { super(msg); }
    }

    private static String form(String... kv) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (b.length() > 0) b.append('&');
            b.append(kv[i]).append('=').append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
        }
        return b.toString();
    }

    private static HttpResponse<String> postForm(String url, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(String url, JsonObject body, String bearer) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (bearer != null) r.header("Authorization", "Bearer " + bearer);
        return HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> r) {
        try { return JsonParser.parseString(r.body()).getAsJsonObject(); } catch (Exception e) { return new JsonObject(); }
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }

    /** Step 1: get a code for the player to type at microsoft.com/link. */
    public static DeviceCode start() throws Exception {
        HttpResponse<String> r = postForm("https://login.live.com/oauth20_connect.srf",
                form("client_id", CLIENT_ID, "scope", SCOPE, "response_type", "device_code"));
        JsonObject o = json(r);
        if (r.statusCode() != 200 || str(o, "user_code") == null) throw new AuthException("Couldn't reach Microsoft (" + r.statusCode() + ")");
        int interval = o.has("interval") ? o.get("interval").getAsInt() : 5;
        long exp = System.currentTimeMillis() + (o.has("expires_in") ? o.get("expires_in").getAsLong() : 900) * 1000L;
        String url = str(o, "verification_uri");
        return new DeviceCode(str(o, "user_code"), str(o, "device_code"), url == null ? "https://www.microsoft.com/link" : url, Math.max(2, interval), exp);
    }

    /** Step 2: poll once. Returns null while the player hasn't finished signing in yet. */
    public static Result poll(DeviceCode code) throws Exception {
        if (System.currentTimeMillis() > code.expiresAt()) throw new AuthException("The code expired - try again");
        HttpResponse<String> r = postForm("https://login.live.com/oauth20_token.srf",
                form("grant_type", "urn:ietf:params:oauth:grant-type:device_code", "client_id", CLIENT_ID, "device_code", code.deviceCode()));
        JsonObject o = json(r);
        if (r.statusCode() == 200) return finish(str(o, "access_token"), str(o, "refresh_token"));
        String err = str(o, "error");
        if ("authorization_pending".equals(err) || "slow_down".equals(err)) return null;
        if ("authorization_declined".equals(err)) throw new AuthException("Sign-in was cancelled");
        if ("expired_token".equals(err)) throw new AuthException("The code expired - try again");
        throw new AuthException("Microsoft sign-in failed (" + (err == null ? r.statusCode() : err) + ")");
    }

    /** Signs in again with a saved refresh token (no browser needed). */
    public static Result refresh(String refreshToken) throws Exception {
        HttpResponse<String> r = postForm("https://login.live.com/oauth20_token.srf",
                form("grant_type", "refresh_token", "client_id", CLIENT_ID, "refresh_token", refreshToken, "scope", SCOPE));
        JsonObject o = json(r);
        if (r.statusCode() != 200) throw new AuthException("Sign-in expired - remove the account and add it again");
        return finish(str(o, "access_token"), str(o, "refresh_token") != null ? str(o, "refresh_token") : refreshToken);
    }

    private static Result finish(String msToken, String refresh) throws Exception {
        if (msToken == null) throw new AuthException("Microsoft sign-in failed");
        // Xbox Live
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", msToken);
        JsonObject xblReq = new JsonObject();
        xblReq.add("Properties", props);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        HttpResponse<String> xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblReq, null);
        if (xbl.statusCode() != 200) throw new AuthException("Xbox Live sign-in failed (" + xbl.statusCode() + ")");
        String xblToken = str(json(xbl), "Token");

        // XSTS
        JsonObject xp = new JsonObject();
        xp.addProperty("SandboxId", "RETAIL");
        JsonArray toks = new JsonArray();
        toks.add(xblToken);
        xp.add("UserTokens", toks);
        JsonObject xstsReq = new JsonObject();
        xstsReq.add("Properties", xp);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        HttpResponse<String> xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq, null);
        JsonObject xo = json(xsts);
        if (xsts.statusCode() != 200) {
            long code = xo.has("XErr") ? xo.get("XErr").getAsLong() : 0;
            if (code == 2148916233L) throw new AuthException("This Microsoft account has no Xbox profile yet - sign in at minecraft.net once");
            if (code == 2148916238L) throw new AuthException("Child accounts need to be added to a family first");
            throw new AuthException("Xbox sign-in failed (" + xsts.statusCode() + ")");
        }
        String xstsToken = str(xo, "Token");
        String uhs = xo.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        String xuid = null;
        try { xuid = xo.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("xid").getAsString(); } catch (Exception ignored) {}

        // Minecraft
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        HttpResponse<String> mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq, null);
        if (mc.statusCode() != 200) throw new AuthException("Minecraft sign-in failed (" + mc.statusCode() + ")");
        String mcToken = str(json(mc), "access_token");

        HttpResponse<String> prof = HTTP.send(HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + mcToken).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (prof.statusCode() == 404) throw new AuthException("This account doesn't own Minecraft: Java Edition");
        if (prof.statusCode() != 200) throw new AuthException("Couldn't load the Minecraft profile (" + prof.statusCode() + ")");
        JsonObject po = json(prof);
        String id = str(po, "id");
        UUID uuid = UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        return new Result(str(po, "name"), uuid, mcToken, refresh, xuid);
    }
}
