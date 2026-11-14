package com.therabbits.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.therabbits.client.RabbitsClient;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A 3D cosmetic (hat or arm piece) loaded from assets/therabbits/cosmetics/&lt;id&gt;.json.
 * Coordinates are in model pixels, local to the head or arm it sits on; each entry becomes a ModelPart.
 */
public final class CosmeticModel {
    public final String id;
    public final boolean arms;
    public final ModelPart root;
    /** Mirrored copy for the left arm (arm pieces only). */
    public final ModelPart mirrored;
    public final Identifier texture;
    public final Identifier glow;

    private CosmeticModel(String id, JsonObject js) {
        this.id = id;
        this.arms = "arms".equals(js.get("attach").getAsString());
        JsonArray ts = js.getAsJsonArray("texture");
        int tw = ts.get(0).getAsInt(), th = ts.get(1).getAsInt();
        this.root = build(js.getAsJsonArray("parts"), tw, th, false);
        this.mirrored = arms ? build(js.getAsJsonArray("parts"), tw, th, true) : null;
        this.texture = Identifier.fromNamespaceAndPath(RabbitsClient.MOD_ID, "textures/cosmetics/" + id + ".png");
        this.glow = js.get("glow").getAsBoolean()
                ? Identifier.fromNamespaceAndPath(RabbitsClient.MOD_ID, "textures/cosmetics/" + id + "_glow.png") : null;
    }

    private static float f(JsonArray a, int i) { return a.get(i).getAsFloat(); }

    private static ModelPart build(JsonArray parts, int tw, int th, boolean mirror) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        float m = mirror ? -1 : 1;
        for (JsonElement el : parts) {
            JsonObject p = el.getAsJsonObject();
            JsonArray from = p.getAsJsonArray("from"), size = p.getAsJsonArray("size"),
                    pivot = p.getAsJsonArray("pivot"), rot = p.getAsJsonArray("rot"), uv = p.getAsJsonArray("uv");
            float x = mirror ? -(f(from, 0) + f(size, 0)) : f(from, 0);
            CubeListBuilder cubes = CubeListBuilder.create().texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt());
            if (mirror) cubes = cubes.mirror();
            cubes = cubes.addBox(x, f(from, 1), f(from, 2), f(size, 0), f(size, 1), f(size, 2));
            float rad = (float) (Math.PI / 180);
            PartPose pose = PartPose.offsetAndRotation(m * f(pivot, 0), f(pivot, 1), f(pivot, 2),
                    f(rot, 0) * rad, m * f(rot, 1) * rad, m * f(rot, 2) * rad);
            String parent = p.has("parent") && !p.get("parent").isJsonNull() ? p.get("parent").getAsString() : null;
            PartDefinition into = parent != null && defs.containsKey(parent) ? defs.get(parent) : mesh.getRoot();
            defs.put(p.get("name").getAsString(), into.addOrReplaceChild(p.get("name").getAsString(), cubes, pose));
        }
        return LayerDefinition.create(mesh, tw, th).bakeRoot();
    }

    private static final Map<String, CosmeticModel> CACHE = new HashMap<>();

    /** Loads (once) the model with this id, or null if it doesn't exist. */
    public static CosmeticModel get(String id) {
        if (id == null || id.equals("none")) return null;
        return CACHE.computeIfAbsent(id, k -> {
            try (InputStream in = CosmeticModel.class.getResourceAsStream("/assets/therabbits/cosmetics/" + k + ".json")) {
                if (in == null) return null;
                return new CosmeticModel(k, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                RabbitsClient.LOG.warn("Couldn't load cosmetic {}", k, e);
                return null;
            }
        });
    }
}
