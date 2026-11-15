package com.therabbits.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import com.therabbits.client.module.visual.ClientFontModule;

public final class Draw {
    private Draw() {}

    public static final Identifier LOGO = Identifier.fromNamespaceAndPath("therabbits", "textures/gui/logo.png");

    public static Font font() { return Minecraft.getInstance().font; }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** True while HUD elements are drawing (set by HudRenderer), so the HUD can use its own font switch. */
    public static boolean hudContext;
    private static String cachedFile;
    private static Style cachedStyle;

    /** Style with the chosen client font, or null when the normal font should be used. */
    private static Style style() {
        ClientFontModule m = ClientFontModule.get();
        if (m == null || !m.enabled() || m.file() == null) return null;
        if (hudContext ? !m.hud.on() : !m.menus.on()) return null;
        if (!m.file().equals(cachedFile)) {
            cachedFile = m.file();
            cachedStyle = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("therabbits", cachedFile)));
        }
        return cachedStyle;
    }

    private static Component styled(String s, Style st) { return Component.literal(s).withStyle(st); }

    private static final java.util.Map<String, Integer> WIDTHS = new java.util.HashMap<>();

    public static int width(String s) {
        Style st = style();
        if (st == null) return font().width(s);
        String key = cachedFile + "|" + s;
        Integer w = WIDTHS.get(key);
        if (w == null) {
            if (WIDTHS.size() > 4000) WIDTHS.clear();
            w = font().width(styled(s, st));
            WIDTHS.put(key, w);
        }
        return w;
    }

    /** Cut a string to fit maxW pixels (adds ".." when cut). */
    private static final java.util.Map<String, String> TRIMS = new java.util.HashMap<>();

    public static String trim(String s, int maxW) {
        String key = (style() == null ? "" : cachedFile) + "|" + maxW + "|" + s;
        String hit = TRIMS.get(key);
        if (hit != null) return hit;
        String out = s;
        if (width(s) > maxW) {
            while (out.length() > 1 && width(out + "..") > maxW) out = out.substring(0, out.length() - 1);
            out = out + "..";
        }
        if (TRIMS.size() > 2000) TRIMS.clear();
        TRIMS.put(key, out);
        return out;
    }

    private static void draw(GuiGraphics g, String s, int x, int y, int color, boolean shadow) {
        Style st = style();
        if (st == null) g.drawString(font(), s, x, y, color, shadow);
        else g.drawString(font(), styled(s, st), x, y, color, shadow);
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) { draw(g, s, x, y, color, false); }

    public static void textShadow(GuiGraphics g, String s, int x, int y, int color) { draw(g, s, x, y, color, true); }

    public static void centered(GuiGraphics g, String s, int cx, int y, int color) { draw(g, s, cx - width(s) / 2, y, color, false); }

    public static void logo(GuiGraphics g, int x, int y, int size) {
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0, 0, size, size, 512, 512, 512, 512);
    }

    /** Premium switch: rounded track, glowing when on, round knob. */
    public static void toggle(GuiGraphics g, int x, int y, boolean on) {
        if (on) glow(g, x, y, 20, 10, 5, Theme.accent(), 3);
        rgrad(g, x, y, 20, 10, 5, on ? Theme.accent() : 0xFF2E2E34, on ? shade(Theme.accent(), 0.75f) : 0xFF222227);
        rrect(g, on ? x + 11 : x + 1, y + 1, 8, 8, 4, on ? 0xFFFFFFFF : 0xFF8C8C94);
    }

    // ---------------------------------------------------------------- rounded shapes
    private static int inset(int r, int i) {
        double d = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - d * d)));
    }

    /** Filled rounded rectangle. */
    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            g.fill(x + in, y + i, x + w - in, y + i + 1, color);
            g.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Rounded rectangle with a vertical gradient. */
    public static void rgrad(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        float k = h <= 1 ? 0 : 1f / (h - 1);
        // only the rounded rows need their own fill; the straight middle is one gradient quad
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            g.fill(x + in, y + i, x + w - in, y + i + 1, lerp(top, bottom, i * k));
            int j = h - 1 - i;
            g.fill(x + in, y + j, x + w - in, y + j + 1, lerp(top, bottom, j * k));
        }
        if (h - 2 * r > 0) g.fillGradient(x, y + r, x + w, y + h - r, lerp(top, bottom, r * k), lerp(top, bottom, (h - 1 - r) * k));
    }

    /** 1px rounded outline. */
    public static void routline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int prev = r;
        for (int i = 0; i < r; i++) {
            int in = inset(r, i), a = Math.min(prev, in + 1);
            g.fill(x + in, y + i, x + Math.max(in + 1, a), y + i + 1, color);
            g.fill(x + w - Math.max(in + 1, a), y + i, x + w - in, y + i + 1, color);
            g.fill(x + in, y + h - 1 - i, x + Math.max(in + 1, a), y + h - i, color);
            g.fill(x + w - Math.max(in + 1, a), y + h - 1 - i, x + w - in, y + h - i, color);
            prev = in;
        }
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    /** Soft glow around a rounded box. */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int color, int spread) {
        int a0 = (color >>> 24) == 0 ? 0xFF : (color >>> 24);
        for (int i = spread; i >= 1; i--) {
            int a = (int) (a0 * 0.16f * (1f - (i - 1f) / spread));
            routline(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, (a << 24) | (color & 0xFFFFFF));
        }
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
            r |= ((int) (ca + (cb - ca) * t) & 0xFF) << s;
        }
        return r;
    }

    public static int shade(int c, float k) {
        int r = (int) (((c >> 16) & 255) * k), gg = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
        return (c & 0xFF000000) | (Math.min(255, r) << 16) | (Math.min(255, gg) << 8) | Math.min(255, b);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
