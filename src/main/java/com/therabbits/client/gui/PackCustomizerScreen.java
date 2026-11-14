package com.therabbits.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resource pack customizer: search, enable/disable, reorder, apply. */
public class PackCustomizerScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable r) {}

    private final Screen parent;
    private final List<String> enabled = new ArrayList<>();   // load order: last = top priority
    private final List<Pack> all = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private EditBox search;
    private double scroll;
    private boolean changed;
    private String status = "";

    public PackCustomizerScreen(Screen parent) {
        super(Component.literal("Pack Customizer"));
        this.parent = parent;
    }

    private PackRepository repo() { return minecraft.getResourcePackRepository(); }

    @Override
    protected void init() {
        PackRepository r = repo();
        r.reload();
        all.clear();
        all.addAll(r.getAvailablePacks());
        enabled.clear();
        enabled.addAll(r.getSelectedIds());
        int pw = panelW();
        search = new EditBox(font, width / 2 - pw / 2 + 8, 34, pw - 16, 14, Component.literal("Search packs"));
        search.setHint(Component.literal("Search packs..."));
        addRenderableWidget(search);
    }

    private int panelW() { return Math.min(420, width - 40); }

    private boolean on(Pack p) { return enabled.contains(p.getId()); }

    private List<Pack> shown() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Pack> out = new ArrayList<>();
        // enabled packs first (top priority first), then the rest alphabetically
        for (int i = enabled.size() - 1; i >= 0; i--) {
            for (Pack p : all) if (p.getId().equals(enabled.get(i))) out.add(p);
        }
        List<Pack> rest = new ArrayList<>();
        for (Pack p : all) if (!on(p)) rest.add(p);
        rest.sort((a, b) -> a.getTitle().getString().compareToIgnoreCase(b.getTitle().getString()));
        out.addAll(rest);
        if (!q.isEmpty()) out.removeIf(p -> !p.getTitle().getString().toLowerCase(Locale.ROOT).contains(q)
                && !p.getId().toLowerCase(Locale.ROOT).contains(q));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        int pw = panelW(), px = width / 2 - pw / 2, top = 10, bottom = height - 10;
        Draw.rect(g, px, top, pw, bottom - top, 0xF00B0B0B);
        Draw.outline(g, px, top, pw, bottom - top, 0x30FFFFFF);
        Draw.text(g, "Pack Customizer", px + 8, top + 8, 0xFFFFFFFF);
        Draw.text(g, "Top of the list wins", px + pw - 8 - Draw.width("Top of the list wins"), top + 8, 0xFF6A6A6A);

        int listY = 54, listH = bottom - 34 - listY, rowH = 26;
        List<Pack> packs = shown();
        scroll = Mth.clamp(scroll, 0, Math.max(0, packs.size() * rowH - listH));
        g.enableScissor(px, listY, px + pw, listY + listH);
        int y = listY - (int) scroll;
        for (Pack p : packs) {
            if (y + rowH >= listY && y <= listY + listH) row(g, p, px + 6, y, pw - 12, rowH - 3, mx, my, listY, listH);
            y += rowH;
        }
        g.disableScissor();
        if (packs.isEmpty()) Draw.centered(g, "No packs match", width / 2, listY + 20, 0xFF7A7A7A);

        int by = bottom - 26;
        button(g, mx, my, px + 8, by, 110, 18, "Open Pack Folder", () ->
                net.minecraft.util.Util.getPlatform().openPath(minecraft.getResourcePackDirectory()));
        button(g, mx, my, px + pw - 8 - 70, by, 70, 18, changed ? "Apply" : "Done", this::applyAndClose);
        if (!status.isEmpty()) Draw.centered(g, status, width / 2, by + 5, 0xFF9A9A9A);
        super.render(g, mx, my, delta);
    }

    private void row(GuiGraphics g, Pack p, int x, int y, int w, int h, int mx, int my, int areaY, int areaH) {
        boolean on = on(p), fixed = p.isRequired();
        boolean hov = Draw.inside(mx, my, x, y, w, h) && my >= areaY && my < areaY + areaH;
        Draw.rect(g, x, y, w, h, hov ? 0xFF1A1A1C : 0xFF121214);
        Draw.outline(g, x, y, w, h, on ? 0x60FFFFFF : 0x20FFFFFF);
        Draw.text(g, Draw.trim(p.getTitle().getString(), w - 120), x + 8, y + 4, on ? 0xFFFFFFFF : 0xFF9A9A9A);
        Draw.text(g, Draw.trim(p.getDescription().getString(), w - 120), x + 8, y + 13, 0xFF5E5E5E);
        int tx = x + w - 30;
        if (fixed) Draw.text(g, "Built-in", x + w - 8 - Draw.width("Built-in"), y + 8, 0xFF5E5E5E);
        else {
            Draw.toggle(g, tx, y + 7, on);
            clip(tx - 4, y, 30, h, areaY, areaH, () -> toggle(p));
            if (on) {
                int ax = tx - 40;
                arrow(g, ax, y + 5, "▲", mx, my, areaY, areaH, () -> move(p, 1));
                arrow(g, ax + 18, y + 5, "▼", mx, my, areaY, areaH, () -> move(p, -1));
            }
        }
    }

    private void arrow(GuiGraphics g, int x, int y, String s, int mx, int my, int areaY, int areaH, Runnable r) {
        boolean h = Draw.inside(mx, my, x, y, 14, 14);
        Draw.rect(g, x, y, 14, 14, h ? 0xFFFFFFFF : 0xFF1E1E20);
        Draw.centered(g, s, x + 7, y + 3, h ? 0xFF000000 : 0xFFCFCFCF);
        clip(x, y, 14, 14, areaY, areaH, r);
    }

    private void clip(int x, int y, int w, int h, int areaY, int areaH, Runnable r) {
        int t = Math.max(y, areaY), b = Math.min(y + h, areaY + areaH);
        if (b > t) hits.add(new Hit(x, t, w, b - t, r));
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, Runnable r) {
        boolean hov = Draw.inside(mx, my, x, y, w, h);
        Draw.rect(g, x, y, w, h, hov ? 0xFFFFFFFF : 0xFF151515);
        Draw.outline(g, x, y, w, h, 0x40FFFFFF);
        Draw.centered(g, label, x + w / 2, y + 5, hov ? 0xFF000000 : 0xFFD0D0D0);
        hits.add(new Hit(x, y, w, h, r));
    }

    private void toggle(Pack p) {
        if (on(p)) enabled.remove(p.getId());
        else enabled.add(p.getId());
        changed = true;
        status = "Press Apply to reload";
    }

    /** dir 1 = higher priority (later in load order). */
    private void move(Pack p, int dir) {
        int i = enabled.indexOf(p.getId()), j = i + dir;
        if (i < 0 || j < 0 || j >= enabled.size()) return;
        String other = enabled.get(j);
        Pack op = repo().getPack(other);
        if (op != null && op.isRequired()) return;   // don't move below vanilla
        enabled.set(j, p.getId());
        enabled.set(i, other);
        changed = true;
        status = "Press Apply to reload";
    }

    private void applyAndClose() {
        if (changed) {
            PackRepository r = repo();
            r.setSelected(enabled);
            minecraft.options.updateResourcePacks(r);
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            if (search.mouseClicked(e, dbl)) return true;
            for (Hit h : List.copyOf(hits)) if (Draw.inside(e.x(), e.y(), h.x(), h.y(), h.w(), h.h())) { h.r().run(); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - sy * 26);
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
