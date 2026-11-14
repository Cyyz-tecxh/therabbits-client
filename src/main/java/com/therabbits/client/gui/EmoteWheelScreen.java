package com.therabbits.client.gui;

import com.therabbits.client.emote.EmoteManager;
import com.therabbits.client.emote.Emotes;
import com.therabbits.client.emote.Emotes.Emote;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** Emote wheel: point at an emote and click (or let go of G). */
public class EmoteWheelScreen extends Screen {
    private final long opened = System.currentTimeMillis();
    private int hovered = -1;

    public EmoteWheelScreen() { super(Component.literal("Emotes")); }

    private List<Emote> list() { return Emotes.ALL; }

    private int radius() { return Math.min(110, Math.min(width, height) / 2 - 30); }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        float open = Mth.clamp((System.currentTimeMillis() - opened) / 140f, 0, 1);
        int cx = width / 2, cy = height / 2, r = (int) (radius() * (0.85f + 0.15f * open));
        List<Emote> l = list();
        int n = l.size();
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy);
        hovered = -1;
        if (dist > 24) {
            double a = Math.atan2(dy, dx) + Math.PI / 2;
            if (a < 0) a += Math.PI * 2;
            hovered = (int) Math.round(a / (Math.PI * 2 / n)) % n;
        }
        // ring
        for (int i = 0; i < 360; i += 2) {
            double a = Math.toRadians(i);
            int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
            g.fill(x - 1, y - 1, x + 1, y + 1, 0x30FFFFFF);
        }
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n - Math.PI / 2;
            int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
            boolean h = i == hovered;
            String name = l.get(i).name();
            int w = Math.max(54, Draw.width(name) + 14), bh = 18;
            Draw.rect(g, x - w / 2, y - bh / 2, w, bh, h ? Theme.accent() : 0xE0141418);
            Draw.outline(g, x - w / 2, y - bh / 2, w, bh, h ? Theme.accent() : 0x40FFFFFF);
            Draw.centered(g, name, x, y - 4, h ? Theme.onAccent() : 0xFFE6E6EA);
        }
        // centre
        Draw.rect(g, cx - 34, cy - 14, 68, 28, 0xE0101014);
        Draw.outline(g, cx - 34, cy - 14, 68, 28, Theme.accent(0x90));
        Draw.centered(g, hovered >= 0 ? l.get(hovered).name() : "Emotes", cx, cy - 8, 0xFFFFFFFF);
        Draw.centered(g, hovered >= 0 ? "click" : "pick one", cx, cy + 3, 0xFF8A8A94);
        Draw.centered(g, "Move to stop an emote", cx, height - 22, 0xFF8A8A94);
        super.render(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            if (hovered >= 0) {
                Emote em = list().get(hovered);
                onClose();
                EmoteManager.play(em);
            } else onClose();
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
