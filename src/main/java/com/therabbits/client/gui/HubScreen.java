package com.therabbits.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Right Shift: logo with three buttons - Emotes, MODS, Cosmetics. */
public class HubScreen extends Screen {
    private static final String[] ICON_EMOTE = {
            "....##.....", "....##.....", "#...........", ".#.####.....", "..##..##....", "......#.#...", ".....#...#..", "....#.....#.", "...#........", "..#.........", ".#.........."};
    private static final String[] ICON_SHIRT = {
            "..##...##..", ".###...###.", "###########", "###########", "...#####...", "...#####...", "...#####...", "...#####...", "...#####...", "...#####...", "..........."};
    private final Screen parent;
    private final long opened = System.currentTimeMillis();
    private String toast;
    private long toastAt;

    public HubScreen(Screen parent) {
        super(Component.literal("TheRabbits"));
        this.parent = parent;
    }

    private int cy() { return height / 2 - 10; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        float t = Mth.clamp((System.currentTimeMillis() - opened) / 160f, 0, 1);
        g.fill(0, 0, width, height, ((int) (t * 0x99)) << 24);
        int cx = width / 2, cy = cy() + (int) ((1 - t) * 8);
        Draw.logo(g, cx - 28, cy - 70, 56);
        // [emote] [  MODS  ] [cosmetics]
        int bw = 100, sq = 22, gap = 5, y = cy;
        int x0 = cx - bw / 2 - gap - sq, x1 = cx - bw / 2, x2 = cx + bw / 2 + gap;
        square(g, x0, y, sq, mx, my, ICON_EMOTE);
        boolean hm = Draw.inside(mx, my, x1, y, bw, sq);
        Draw.rect(g, x1, y, bw, sq, hm ? Theme.accent(0xF0) : 0xE0161619);
        Draw.outline(g, x1, y, bw, sq, 0x50FFFFFF);
        Draw.centered(g, "MODS", cx, y + 7, hm ? Theme.onAccent() : 0xFFEDEDED);
        square(g, x2, y, sq, mx, my, ICON_SHIRT);
        String hint = Draw.inside(mx, my, x0, y, sq, sq) ? "Emotes" : Draw.inside(mx, my, x2, y, sq, sq) ? "Cosmetics" : hm ? "Mods & settings" : "";
        if (!hint.isEmpty()) Draw.centered(g, hint, cx, y + sq + 8, 0xFF9A9A9A);
        if (toast != null && System.currentTimeMillis() - toastAt < 1800) Draw.centered(g, toast, cx, y + sq + 22, 0xFFFFFFFF);
        super.render(g, mx, my, delta);
    }

    private static void square(GuiGraphics g, int x, int y, int s, int mx, int my, String[] icon) {
        boolean h = Draw.inside(mx, my, x, y, s, s);
        Draw.rect(g, x, y, s, s, h ? Theme.accent(0xF0) : 0xE0161619);
        Draw.outline(g, x, y, s, s, 0x50FFFFFF);
        int c = h ? Theme.onAccent() : 0xFFBDBDBD;
        for (int r = 0; r < icon.length; r++)
            for (int col = 0; col < icon[r].length(); col++)
                if (icon[r].charAt(col) == '#') g.fill(x + 6 + col, y + 6 + r, x + 7 + col, y + 7 + r, c);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        if (minecraft.level == null) super.renderBackground(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            int cx = width / 2, y = cy(), bw = 100, sq = 22, gap = 5;
            int x0 = cx - bw / 2 - gap - sq, x1 = cx - bw / 2, x2 = cx + bw / 2 + gap;
            if (Draw.inside(e.x(), e.y(), x1, y, bw, sq)) { minecraft.setScreen(new ModMenuScreen(parent)); return true; }
            if (Draw.inside(e.x(), e.y(), x2, y, sq, sq)) { minecraft.setScreen(new CosmeticsScreen(parent)); return true; }
            if (Draw.inside(e.x(), e.y(), x0, y, sq, sq)) { toast = "Emotes are coming soon"; toastAt = System.currentTimeMillis(); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
