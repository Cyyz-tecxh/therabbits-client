package com.therabbits.client.gui;

import com.therabbits.client.Config;
import com.therabbits.client.ModuleManager;
import com.therabbits.client.hud.HudRenderer;
import com.therabbits.client.module.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag HUD elements around. Elements snap to screen edges and centre lines. */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 4;
    private final Screen parent;
    private HudModule dragging;
    private double grabX, grabY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.fill(width / 2, 0, width / 2 + 1, height, 0x30FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x30FFFFFF);

        for (HudModule h : ModuleManager.hud()) {
            if (!h.enabled()) continue;
            HudRenderer.draw(g, h, true);
            boolean hover = h == dragging || Draw.inside(mouseX, mouseY, (int) h.x, (int) h.y, h.scaledWidth(), h.scaledHeight());
            Draw.outline(g, (int) h.x - 1, (int) h.y - 1, h.scaledWidth() + 2, h.scaledHeight() + 2,
                    hover ? 0xFFFFFFFF : 0x80FFFFFF);
            if (hover) Draw.textShadow(g, h.name(), (int) h.x, (int) h.y - 11, Theme.TEXT);
        }
        Draw.centered(g, "Drag elements to move them  -  Esc to go back", width / 2, height - 14, Theme.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            var list = ModuleManager.hud();
            for (int i = list.size() - 1; i >= 0; i--) {
                HudModule h = list.get(i);
                if (h.enabled() && Draw.inside(event.x(), event.y(), (int) h.x, (int) h.y, h.scaledWidth(), h.scaledHeight())) {
                    dragging = h;
                    grabX = event.x() - h.x;
                    grabY = event.y() - h.y;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            int w = dragging.scaledWidth(), h = dragging.scaledHeight();
            double nx = event.x() - grabX, ny = event.y() - grabY;
            nx = snap(nx, w, width);
            ny = snap(ny, h, height);
            dragging.x = Math.max(0, Math.min(width - w, nx));
            dragging.y = Math.max(0, Math.min(height - h, ny));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    private static double snap(double pos, int size, int screen) {
        if (Math.abs(pos) < SNAP) return 0;
        if (Math.abs(pos + size - screen) < SNAP) return screen - size;
        double centre = screen / 2.0 - size / 2.0;
        if (Math.abs(pos - centre) < SNAP) return centre;
        return pos;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
