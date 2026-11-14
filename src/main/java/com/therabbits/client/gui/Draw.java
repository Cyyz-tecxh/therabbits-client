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

    public static int width(String s) {
        Style st = style();
        return st == null ? font().width(s) : font().width(styled(s, st));
    }

    /** Cut a string to fit maxW pixels (adds ".." when cut). */
    public static String trim(String s, int maxW) {
        if (width(s) <= maxW) return s;
        String out = s;
        while (out.length() > 1 && width(out + "..") > maxW) out = out.substring(0, out.length() - 1);
        return out + "..";
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

    public static void toggle(GuiGraphics g, int x, int y, boolean on) {
        rect(g, x, y, 18, 10, on ? Theme.ON : Theme.OFF);
        rect(g, on ? x + 9 : x + 1, y + 1, 8, 8, on ? 0xFF000000 : 0xFF9A9A9A);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
