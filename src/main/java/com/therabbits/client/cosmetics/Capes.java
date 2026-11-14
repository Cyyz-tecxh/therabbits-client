package com.therabbits.client.cosmetics;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** The TheRabbits capes. Ids are what the cosmetics server stores. Each cape is a 16-frame animation. */
public final class Capes {
    private Capes() {}

    public static final String[] IDS = {"none", "sheen_black", "sheen_white", "pulse", "scanline"};
    public static final String[] NAMES = {"None", "Sheen Black", "Sheen White", "Pulse", "Scanline"};
    private static final int FRAMES = 16, FRAME_MS = 125;
    private static final Map<String, ClientAsset.Texture[]> TEXTURES = new HashMap<>();

    public static boolean valid(String id) {
        for (String s : IDS) if (s.equals(id)) return true;
        return false;
    }

    /** Current animation frame for a cape id, or null for none/unknown. */
    public static ClientAsset.Texture texture(String id) {
        if (id == null || id.equals("none") || !valid(id)) return null;
        ClientAsset.Texture[] frames = TEXTURES.computeIfAbsent(id, k -> {
            ClientAsset.Texture[] out = new ClientAsset.Texture[FRAMES];
            for (int i = 0; i < FRAMES; i++) {
                Identifier path = Identifier.fromNamespaceAndPath("therabbits", "textures/capes/" + k + "/" + i + ".png");
                out[i] = new ClientAsset.Texture() {
                    @Override public Identifier id() { return path; }
                    @Override public Identifier texturePath() { return path; }
                };
            }
            return out;
        });
        return frames[(int) ((System.currentTimeMillis() / FRAME_MS) % FRAMES)];
    }
}
