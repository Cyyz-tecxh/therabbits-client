package com.therabbits.client.cosmetics;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** The TheRabbits capes. Ids are what the cosmetics server stores. */
public final class Capes {
    private Capes() {}

    public static final String[] IDS = {"none", "trlh_black", "trlh_white", "lightning", "moonlit"};
    public static final String[] NAMES = {"None", "TRLH Black", "TRLH White", "Lightning", "Moonlit"};
    private static final Map<String, ClientAsset.Texture> TEXTURES = new HashMap<>();

    public static boolean valid(String id) {
        for (String s : IDS) if (s.equals(id)) return true;
        return false;
    }

    /** Texture for a cape id, or null for none/unknown. */
    public static ClientAsset.Texture texture(String id) {
        if (id == null || id.equals("none") || !valid(id)) return null;
        return TEXTURES.computeIfAbsent(id, k -> {
            Identifier path = Identifier.fromNamespaceAndPath("therabbits", "textures/capes/" + k + ".png");
            return new ClientAsset.Texture() {
                @Override public Identifier id() { return path; }
                @Override public Identifier texturePath() { return path; }
            };
        });
    }
}
