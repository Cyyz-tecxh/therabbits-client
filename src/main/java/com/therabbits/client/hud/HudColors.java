package com.therabbits.client.hud;

import java.awt.Color;

/** Named colors for HUD text. "Chroma" is an animated rainbow. */
public final class HudColors {
    private HudColors() {}

    public static final String[] NAMES = {"White", "Light Gray", "Gray", "Red", "Orange", "Yellow", "Lime",
            "Green", "Aqua", "Blue", "Purple", "Pink", "Chroma"};
    private static final int[] RGB = {0xFFFFFF, 0xD0D0D0, 0x909090, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55,
            0x00C040, 0x55FFFF, 0x5577FF, 0xAA55FF, 0xFF77DD, 0};

    /** RGB (no alpha) for a palette index; Chroma shifts hue over time (offset lets lines differ). */
    public static int rgb(int index, float offset) {
        if (NAMES[index].equals("Chroma")) {
            float hue = ((System.currentTimeMillis() % 4000) / 4000f + offset) % 1f;
            return Color.HSBtoRGB(hue, 0.65f, 1f) & 0xFFFFFF;
        }
        return RGB[index];
    }

    /** Names with "Global" first, for per-element overrides. */
    public static String[] withGlobal() {
        String[] out = new String[NAMES.length + 1];
        out[0] = "Global";
        System.arraycopy(NAMES, 0, out, 1, NAMES.length);
        return out;
    }
}
