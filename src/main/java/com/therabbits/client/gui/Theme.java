package com.therabbits.client.gui;

/** Black & white palette (ARGB). */
public final class Theme {
    private Theme() {}

    public static final int BACKGROUND = 0xE0050505;
    public static final int PANEL      = 0xF0101010;
    public static final int RAISED     = 0xFF1A1A1A;
    public static final int HOVER      = 0xFF262626;
    public static final int BORDER     = 0xFF3A3A3A;
    public static final int ACCENT     = 0xFFFFFFFF;
    public static final int TEXT       = 0xFFF2F2F2;
    public static final int TEXT_DIM   = 0xFFA0A0A0;
    public static final int TEXT_MUTED = 0xFF6A6A6A;
    public static final int ON         = 0xFFFFFFFF;
    public static final int OFF        = 0xFF3C3C3C;
    public static final int HUD_BG     = 0x90000000;

    private static final int[] ACCENTS = {0xFF6EC6FF, 0xFFFFFFFF, 0xFF9B5CFF, 0xFF3D8BFF, 0xFF38D6F5, 0xFF46D97A, 0xFFFF4B5C, 0xFFFF6FB5, 0xFFFF9A3C};
    public static final String[] ACCENT_NAMES = {"Light Blue", "White", "Purple", "Blue", "Cyan", "Green", "Red", "Pink", "Orange"};

    /** Menu highlight colour (Client Settings -> Accent Colour). */
    public static int accent() {
        var cs = com.therabbits.client.module.utility.ClientSettingsModule.get();
        int i = cs == null ? 0 : cs.accent.get();
        return ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, i))];
    }

    /** Text colour that reads well on top of the accent. */
    public static int onAccent() {
        int c = accent();
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF000000 : 0xFFFFFFFF;
    }

    /** Accent with a custom alpha (0-255). */
    public static int accent(int alpha) { return (alpha << 24) | (accent() & 0xFFFFFF); }
}
