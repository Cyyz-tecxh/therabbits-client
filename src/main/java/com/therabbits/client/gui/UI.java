package com.therabbits.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * TheRabbits design system: one palette, one corner style, one set of controls.
 * Minecraft first: vanilla font, crisp 1px pixel corners, flat surfaces, no glow.
 * Ice blue is used sparingly: selection bars, enabled switches, focused fields.
 */
public final class UI {
    private UI() {}

    // ---- palette (ARGB) ----
    public static final int BG       = 0xF2101419;   // window (slightly see-through so the world shows)
    public static final int SURFACE  = 0xFF171D24;   // sidebar, inputs
    public static final int PANEL    = 0xFF202832;   // rows, cards
    public static final int PANEL_HI = 0xFF27313D;   // hover
    public static final int BORDER   = 0xFF303B46;
    public static final int LINE     = 0x14FFFFFF;   // hairline dividers
    public static final int TEXT     = 0xFFF4F7FA;
    public static final int MUTED    = 0xFF9BA8B5;
    public static final int FAINT    = 0xFF5E6A76;
    public static final int ICE      = 0xFF8BDCFF;
    public static final int ICE_INK  = 0xFF0B2230;   // text on ice blue
    public static final int GOOD = 0xFF7BE0A6, WARN = 0xFFF2C46D, BAD = 0xFFF2797B;

    /** Accent (Client Settings can still change it; ice blue by default). */
    public static int accent() { return Theme.accent(); }
    public static int accent(int a) { return Theme.accent(a); }

    // ---- shapes: pixel-cut corners (1px), the Minecraft way ----
    public static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
    }

    public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Filled box with a 1px border. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        box(g, x, y, w, h, fill);
        border(g, x, y, w, h, edge);
    }

    /** Soft drop shadow under floating panels (a few translucent rings, no blur). */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 1; i <= 6; i++) box(g, x - i, y - i + 3, w + 2 * i, h + 2 * i, 0x0A000000);
    }

    // ---- controls ----
    /** Rectangular switch, 16x8. {@code k} is 0..1 for the slide animation. */
    public static void toggle(GuiGraphics g, int x, int y, float k) {
        int track = Draw.lerp(0xFF2C3540, accent(), k);
        box(g, x, y, 16, 8, track);
        int kx = x + 1 + Math.round(8 * k);
        g.fill(kx, y + 1, kx + 6, y + 7, Draw.lerp(0xFF8A96A3, ICE_INK, k));
    }

    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean active) {
        card(g, x, y, w, h, SURFACE, active ? accent() : hover ? 0xFF3C4955 : BORDER);
    }

    /** Secondary button: flat panel, accent text on hover. Primary: filled accent. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hover, boolean primary) {
        if (primary) box(g, x, y, w, h, hover ? Draw.shade(accent(), 1.08f) : accent());
        else card(g, x, y, w, h, hover ? PANEL_HI : PANEL, hover ? 0xFF3C4955 : BORDER);
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2 + 1, primary ? ICE_INK : hover ? TEXT : 0xFFD8DEE4);
    }

    /** Small segmented / filter chip. */
    public static void chip(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, boolean hover) {
        if (on) card(g, x, y, w, h, PANEL, BORDER);
        else if (hover) box(g, x, y, w, h, 0x10FFFFFF);
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2 + 1, on ? TEXT : hover ? 0xFFC8D0D8 : MUTED);
    }

    /** Thin slider: track, accent fill, square knob. */
    public static void slider(GuiGraphics g, int x, int cy, int w, float f, boolean hover) {
        g.fill(x, cy - 1, x + w, cy + 1, 0xFF2C3540);
        int kx = x + Math.round(f * w);
        g.fill(x, cy - 1, kx, cy + 1, accent());
        box(g, kx - 3, cy - 4, 7, 8, hover ? 0xFFFFFFFF : 0xFFE6ECF2);
        border(g, kx - 3, cy - 4, 7, 8, 0xFF0E1216);
    }

    public static void label(GuiGraphics g, String s, int x, int y) { Draw.text(g, s.toUpperCase(java.util.Locale.ROOT), x, y, FAINT); }

    /** 5x5-ish pixel glyphs drawn from strings ('#' = pixel). */
    public static void glyph(GuiGraphics g, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++)
                if (rows[r].charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
    }

    public static final String[] STAR = {"..#..", ".###.", "#####", ".###.", ".#.#."};
    public static final String[] STAR_EMPTY = {"..#..", ".#.#.", "#...#", ".#.#.", ".#.#."};
    public static final String[] CHEV_DOWN = {"#...#", ".#.#.", "..#.."};
    public static final String[] CHEV_RIGHT = {"#..", ".#.", "..#", ".#.", "#.."};
    public static final String[] SEARCH = {".###..", "#...#.", "#...#.", "#...#.", ".###..", ".....#"};
    public static final String[] CLOSE = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
}
