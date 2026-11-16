package com.therabbits.client.gui;

import com.therabbits.client.Config;
import com.therabbits.client.ModuleManager;
import com.therabbits.client.hud.HudRenderer;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor: drag elements, scroll to scale, snap to edges / centre / other elements / an optional grid.
 * Left panel: show or hide every element. Top bar: grid, reset, profiles, done.
 */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 4, PANEL_W = 112;
    private static boolean grid;            // remembered for the session
    private static boolean panelOpen = true;
    private final Screen parent;
    private HudModule dragging, selected;
    private double grabX, grabY;
    private Integer guideX, guideY;         // alignment lines shown while dragging
    private String toast;
    private long toastAt, lastClick;
    private HudModule lastClicked;
    private double panelScroll;
    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private final List<Hit> hits = new ArrayList<>();

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
    }

    private int gridSize() { return 8; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x50050809);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        hits.clear();
        if (grid) {
            int s = gridSize();
            for (int x = 0; x < width; x += s) g.fill(x, 0, x + 1, height, (x % (s * 4) == 0) ? 0x16FFFFFF : 0x0AFFFFFF);
            for (int y = 0; y < height; y += s) g.fill(0, y, width, y + 1, (y % (s * 4) == 0) ? 0x16FFFFFF : 0x0AFFFFFF);
        }
        // centre lines, faint; alignment guides, ice blue while dragging
        g.fill(width / 2, 0, width / 2 + 1, height, 0x14FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x14FFFFFF);
        if (dragging != null && guideX != null) g.fill(guideX, 0, guideX + 1, height, UI.accent(0xB0));
        if (dragging != null && guideY != null) g.fill(0, guideY, width, guideY + 1, UI.accent(0xB0));

        HudModule hovered = null;
        for (HudModule h : ModuleManager.hud()) {
            if (!h.enabled()) continue;
            HudRenderer.draw(g, h, true);
            int x = (int) h.x, y = (int) h.y, w = h.scaledWidth(), hh = h.scaledHeight();
            boolean hover = Draw.inside(mx, my, x, y, w, hh);
            if (hover) hovered = h;
            if (h == selected || h == dragging) {
                UI.border(g, x - 2, y - 2, w + 4, hh + 4, UI.accent());
                for (int[] c : new int[][]{{x - 3, y - 3}, {x + w, y - 3}, {x - 3, y + hh}, {x + w, y + hh}})
                    g.fill(c[0], c[1], c[0] + 3, c[1] + 3, 0xFFFFFFFF);
            } else UI.border(g, x - 1, y - 1, w + 2, hh + 2, hover ? UI.accent(0x90) : 0x30FFFFFF);
        }
        // name + scale chip for the hovered / selected element
        HudModule tag = dragging != null ? dragging : hovered != null ? hovered : selected;
        if (tag != null) {
            String s = tag.name() + "  " + Math.round(tag.scale.get() * 100) + "%";
            int tw = Draw.width(s) + 10, tx = (int) Math.min(width - tw - 2, Math.max(2, tag.x)), ty = (int) tag.y - 15;
            if (ty < 22) ty = (int) tag.y + tag.scaledHeight() + 4;
            UI.card(g, tx, ty, tw, 12, UI.BG, UI.BORDER);
            Draw.text(g, s, tx + 5, ty + 2, UI.TEXT);
        }

        renderTopBar(g, mx, my);
        renderPanel(g, mx, my);
        String help = "Drag to move  ·  Scroll to resize  ·  Right-click to hide  ·  Double-click for settings  ·  R resets";
        int hw = Draw.width(help) + 14;
        UI.card(g, width / 2 - hw / 2, height - 17, hw, 13, UI.BG, UI.BORDER);
        Draw.centered(g, help, width / 2, height - 14, UI.MUTED);
        if (toast != null && System.currentTimeMillis() - toastAt < 1600) {
            int tw = Draw.width(toast) + 14;
            UI.card(g, width / 2 - tw / 2, 30, tw, 14, UI.SURFACE, UI.accent(0x90));
            Draw.centered(g, toast, width / 2, 33, UI.TEXT);
        }
    }

    // ---------- top bar ----------
    private void renderTopBar(GuiGraphics g, int mx, int my) {
        String[] labels = {"Grid", "Reset all", "Profiles", "Done"};
        int[] ws = new int[labels.length];
        int total = Draw.width("HUD Editor") + 16;
        for (int i = 0; i < labels.length; i++) { ws[i] = Draw.width(labels[i]) + 14; total += ws[i] + 3; }
        int x = width / 2 - total / 2, y = 6;
        UI.card(g, x - 4, y - 3, total + 8, 20, UI.BG, UI.BORDER);
        Draw.text(g, "HUD Editor", x + 2, y + 4, UI.TEXT);
        int bx = x + Draw.width("HUD Editor") + 14;
        Runnable[] acts = {
                () -> { grid = !grid; toast(grid ? "Snap to grid on" : "Snap to grid off"); },
                () -> { for (HudModule h : ModuleManager.hud()) { h.x = h.defaultX; h.y = h.defaultY; h.scale.reset(); } toast("All elements reset"); },
                () -> { ModMenuScreen m = new ModMenuScreen(this); minecraft.setScreen(m); m.showPresets(); },
                this::onClose};
        for (int i = 0; i < labels.length; i++) {
            boolean hover = Draw.inside(mx, my, bx, y, ws[i], 14);
            boolean primary = i == labels.length - 1;
            if (i == 0 && grid) UI.card(g, bx, y, ws[i], 14, UI.PANEL, UI.accent());
            else UI.button(g, bx, y, ws[i], 14, labels[i], hover, primary);
            if (i == 0 && grid) Draw.centered(g, labels[i], bx + ws[i] / 2, y + 4, UI.accent());
            hits.add(new Hit(bx, y, ws[i], 14, acts[i]));
            bx += ws[i] + 3;
        }
    }

    // ---------- left panel: visibility ----------
    private void renderPanel(GuiGraphics g, int mx, int my) {
        int x = 6, y = 30;
        List<HudModule> list = ModuleManager.hud();
        int rowH = 13, h = Math.min(height - y - 26, 20 + list.size() * rowH);
        if (!panelOpen) {
            boolean hv = Draw.inside(mx, my, x, y, 58, 14);
            UI.button(g, x, y, 58, 14, "Elements", hv, false);
            hits.add(new Hit(x, y, 58, 14, () -> panelOpen = true));
            return;
        }
        UI.card(g, x, y, PANEL_W, h, UI.BG, UI.BORDER);
        UI.label(g, "Elements", x + 6, y + 6);
        boolean ch = Draw.inside(mx, my, x + PANEL_W - 12, y + 4, 9, 9);
        UI.glyph(g, UI.CLOSE, x + PANEL_W - 10, y + 6, ch ? UI.TEXT : UI.FAINT);
        hits.add(new Hit(x + PANEL_W - 12, y + 4, 10, 10, () -> panelOpen = false));
        int listY = y + 18, listH = h - 20;
        int content = list.size() * rowH;
        panelScroll = Math.max(0, Math.min(panelScroll, Math.max(0, content - listH)));
        g.enableScissor(x + 1, listY, x + PANEL_W - 1, listY + listH);
        int ry = listY - (int) panelScroll;
        for (HudModule m : list) {
            if (ry + rowH >= listY && ry <= listY + listH) {
                boolean hv = Draw.inside(mx, my, x + 2, ry, PANEL_W - 4, rowH) && my >= listY && my < listY + listH;
                if (hv || m == selected) UI.box(g, x + 2, ry, PANEL_W - 4, rowH, m == selected ? UI.PANEL : 0x0CFFFFFF);
                Draw.text(g, Draw.trim(m.name(), PANEL_W - 34), x + 7, ry + 3, m.enabled() ? UI.TEXT : UI.FAINT);
                UI.toggle(g, x + PANEL_W - 22, ry + 3, m.enabled() ? 1 : 0);
                if (my >= listY && my < listY + listH)
                    hits.add(new Hit(x + 2, ry, PANEL_W - 4, rowH, () -> { m.toggle(); if (m.enabled()) selected = m; }));
            }
            ry += rowH;
        }
        g.disableScissor();
    }

    private void toast(String s) { toast = s; toastAt = System.currentTimeMillis(); }

    // ---------- input ----------
    private HudModule elementAt(double x, double y) {
        var list = ModuleManager.hud();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule h = list.get(i);
            if (h.enabled() && Draw.inside(x, y, (int) h.x, (int) h.y, h.scaledWidth(), h.scaledHeight())) return h;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0)
            for (Hit h : List.copyOf(hits))
                if (Draw.inside(event.x(), event.y(), h.x(), h.y(), h.w(), h.h())) { h.action().run(); return true; }
        HudModule h = elementAt(event.x(), event.y());
        if (event.button() == 1 && h != null) {   // right-click hides
            h.setEnabled(false);
            if (selected == h) selected = null;
            toast(h.name() + " hidden  ·  turn it back on in Elements");
            return true;
        }
        if (event.button() == 0) {
            long now = System.currentTimeMillis();
            if (h != null && h == lastClicked && now - lastClick < 300) {   // double-click: settings
                ModMenuScreen m = new ModMenuScreen(this);
                minecraft.setScreen(m);
                m.openSettings(h);
                return true;
            }
            lastClicked = h; lastClick = now;
            selected = h;
            if (h != null) {
                dragging = h;
                grabX = event.x() - h.x;
                grabY = event.y() - h.y;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            int w = dragging.scaledWidth(), h = dragging.scaledHeight();
            double nx = event.x() - grabX, ny = event.y() - grabY;
            guideX = null; guideY = null;
            if (grid) {
                nx = Math.round(nx / gridSize()) * gridSize();
                ny = Math.round(ny / gridSize()) * gridSize();
            } else {
                nx = snapAxis(nx, w, width, true);
                ny = snapAxis(ny, h, height, false);
            }
            dragging.x = Math.max(0, Math.min(width - w, nx));
            dragging.y = Math.max(0, Math.min(height - h, ny));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    /** Snap to the screen edges, the centre line, and the edges / centres of the other elements. */
    private double snapAxis(double pos, int size, int screen, boolean horizontal) {
        List<double[]> targets = new ArrayList<>();   // {target position for our start, guide line}
        targets.add(new double[]{0, -1});
        targets.add(new double[]{screen - size, -1});
        targets.add(new double[]{screen / 2.0 - size / 2.0, screen / 2.0});
        for (HudModule o : ModuleManager.hud()) {
            if (!o.enabled() || o == dragging) continue;
            double a = horizontal ? o.x : o.y, l = horizontal ? o.scaledWidth() : o.scaledHeight();
            targets.add(new double[]{a, a});                    // align starts
            targets.add(new double[]{a + l - size, a + l});     // align ends
            targets.add(new double[]{a + l + 2, -1});           // sit right after it
            targets.add(new double[]{a - size - 2, -1});        // sit right before it
        }
        for (double[] t : targets) {
            if (Math.abs(pos - t[0]) < SNAP) {
                if (t[1] >= 0) { if (horizontal) guideX = (int) t[1]; else guideY = (int) t[1]; }
                return t[0];
            }
        }
        return pos;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null; guideX = null; guideY = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (panelOpen && Draw.inside(mouseX, mouseY, 6, 30, PANEL_W, height - 56)) { panelScroll -= scrollY * 13; return true; }
        HudModule h = elementAt(mouseX, mouseY);
        if (h == null) h = selected;
        if (h != null) {   // resize around the element's centre
            double cx = h.x + h.scaledWidth() / 2.0, cy = h.y + h.scaledHeight() / 2.0;
            h.scale.set(h.scale.get() + Math.signum(scrollY) * 0.05);
            h.x = Math.max(0, Math.min(width - h.scaledWidth(), cx - h.scaledWidth() / 2.0));
            h.y = Math.max(0, Math.min(height - h.scaledHeight(), cy - h.scaledHeight() / 2.0));
            selected = h;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (selected != null) {
            int d = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            switch (k) {
                case GLFW.GLFW_KEY_LEFT -> { selected.x = Math.max(0, selected.x - d); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { selected.x = Math.min(width - selected.scaledWidth(), selected.x + d); return true; }
                case GLFW.GLFW_KEY_UP -> { selected.y = Math.max(0, selected.y - d); return true; }
                case GLFW.GLFW_KEY_DOWN -> { selected.y = Math.min(height - selected.scaledHeight(), selected.y + d); return true; }
                case GLFW.GLFW_KEY_R -> { selected.x = selected.defaultX; selected.y = selected.defaultY; selected.scale.reset(); toast(selected.name() + " reset"); return true; }
                case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> { selected.setEnabled(false); toast(selected.name() + " hidden"); selected = null; return true; }
                default -> {}
            }
        }
        if (k == GLFW.GLFW_KEY_G) { grid = !grid; toast(grid ? "Snap to grid on" : "Snap to grid off"); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
