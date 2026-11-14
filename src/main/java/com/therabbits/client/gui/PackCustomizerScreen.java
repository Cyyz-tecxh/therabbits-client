package com.therabbits.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.therabbits.client.RabbitsClient;
import com.therabbits.client.pack.PackMixer;
import com.therabbits.client.pack.PackMixer.Category;
import com.therabbits.client.pack.PackMixer.Source;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pack Customizer.
 *  MIX   - a tile per item (totem, pearl, sword ...). Click to pick which of your packs it comes from,
 *          right-click to go back to vanilla. Your picks become one pack, "TheRabbits Mix".
 *  PACKS - turn whole packs on/off and change their order.
 */
public class PackCustomizerScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {}
    private record Preview(Identifier id, int w, int h) {}

    private enum Tab { MIX, PACKS }
    private enum Filter { ALL, CHANGED, AVAILABLE }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final List<String> enabled = new ArrayList<>();
    private final List<Pack> all = new ArrayList<>();
    private static final Map<String, Optional<Preview>> PREVIEWS = new HashMap<>();

    private Tab tab = Tab.MIX;
    private Filter filter = Filter.ALL;
    private Category picking;            // non-null = choosing a pack for this item
    private EditBox search;
    private double scroll;
    private boolean mixChanged, listChanged;
    private String tooltip;
    private String status = "";
    private long statusAt;

    public PackCustomizerScreen(Screen parent) {
        super(Component.literal("Pack Customizer"));
        this.parent = parent;
    }

    private int pw() { return Math.min(560, width - 24); }
    private int ph() { return Math.min(340, height - 20); }
    private int px() { return width / 2 - pw() / 2; }
    private int py() { return height / 2 - ph() / 2; }

    @Override
    protected void init() {
        PackMixer.scan();
        PackRepository r = minecraft.getResourcePackRepository();
        r.reload();
        all.clear();
        all.addAll(r.getAvailablePacks());
        enabled.clear();
        enabled.addAll(r.getSelectedIds());
        search = new EditBox(font, px() + pw() - 150, py() + 28, 140, 14, Component.literal("Search"));
        search.setHint(Component.literal("Search..."));
        addRenderableWidget(search);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        tooltip = null;
        int x = px(), y = py(), w = pw(), h = ph(), acc = Theme.accent();
        for (int i = 1; i <= 6; i++) g.fill(x - i, y - i + 3, x + w + i, y + h + i + 3, 0x0C000000);
        Draw.rect(g, x, y, w, h, 0xF20C0C0F);
        Draw.outline(g, x, y, w, h, 0x30FFFFFF);
        g.fill(x, y, x + w, y + 1, Theme.accent(0xB0));

        // header: logo, tabs, close
        Draw.logo(g, x + 8, y + 5, 16);
        int tx = x + w / 2 - 70;
        for (Tab t : Tab.values()) {
            String label = t == Tab.MIX ? "MIX" : "PACKS";
            boolean sel = tab == t, hov = Draw.inside(mx, my, tx, y + 6, 66, 14);
            Draw.rect(g, tx, y + 6, 66, 14, sel ? acc : hov ? 0xFF26262A : 0xFF16161A);
            Draw.centered(g, label, tx + 33, y + 9, sel ? Theme.onAccent() : 0xFFB8B8C0);
            hits.add(new Hit(tx, y + 6, 66, 14, () -> { tab = t; picking = null; scroll = 0; }, null));
            tx += 72;
        }
        iconButton(g, mx, my, x + w - 20, y + 5, "×", "Close", this::onClose);
        g.fill(x + 8, y + 24, x + w - 8, y + 25, 0x18FFFFFF);

        if (tab == Tab.MIX) {
            if (picking == null) renderMix(g, x, y, w, h, mx, my);
            else renderPicker(g, x, y, w, h, mx, my);
        } else {
            renderPacks(g, x, y, w, h, mx, my);
        }

        // footer
        int fy = y + h - 20;
        g.fill(x + 8, fy - 4, x + w - 8, fy - 3, 0x18FFFFFF);
        button(g, mx, my, x + 8, fy, 78, 14, "Open Folder", false, () ->
                net.minecraft.util.Util.getPlatform().openPath(minecraft.getResourcePackDirectory()));
        String hint = tab == Tab.MIX ? (picking == null ? "Click a tile to pick a pack  ·  right-click: vanilla" : "Click a pack to use it  ·  right-click: vanilla")
                : "Toggle packs  ·  arrows change the order";
        if (status != null && !status.isEmpty() && System.currentTimeMillis() - statusAt < 3000) hint = status;
        Draw.centered(g, hint, x + w / 2, fy + 3, 0xFF7E7E88);
        boolean pending = mixChanged || listChanged;
        button(g, mx, my, x + w - 8 - 60, fy, 60, 14, pending ? "Apply" : "Done", pending, this::applyAndClose);
        if (tab == Tab.MIX && PackMixer.changedCount() > 0)
            button(g, mx, my, x + w - 8 - 60 - 66, fy, 60, 14, "Reset all", false, () -> { PackMixer.resetAll(); mixChanged = true; note("Everything back to vanilla"); });

        search.setX(x + w - 150); search.setY(y + 28);
        search.visible = picking == null;
        super.render(g, mx, my, delta);
        if (tooltip != null) {
            String[] lines = tooltip.split("\n");
            int tw = 0;
            for (String l : lines) tw = Math.max(tw, Draw.width(l));
            int ttx = Math.min(mx + 10, width - tw - 10), tty = my + 10;
            Draw.rect(g, ttx - 4, tty - 3, tw + 8, lines.length * 10 + 5, 0xF0141418);
            Draw.outline(g, ttx - 4, tty - 3, tw + 8, lines.length * 10 + 5, Theme.accent(0x90));
            for (int i = 0; i < lines.length; i++) Draw.text(g, lines[i], ttx, tty + i * 10, i == 0 ? Theme.accent() : 0xFFAFAFB8);
        }
    }

    private List<Category> categories() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Category> out = new ArrayList<>();
        for (Category c : PackMixer.CATEGORIES) {
            if (!q.isEmpty() && !c.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            if (filter == Filter.CHANGED && PackMixer.choice(c) == null) continue;
            if (filter == Filter.AVAILABLE && PackMixer.sourcesFor(c).isEmpty()) continue;
            out.add(c);
        }
        return out;
    }

    private void renderMix(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Draw.text(g, "Your setup", x + 10, y + 31, 0xFFFFFFFF);
        Draw.text(g, PackMixer.changedCount() + " of " + PackMixer.CATEGORIES.size() + " changed · " + PackMixer.sources().size() + " packs",
                x + 70, y + 31, 0xFF7E7E88);
        int fx = x + 10;
        for (Filter f : Filter.values()) {
            String label = f == Filter.ALL ? "All" : f == Filter.CHANGED ? "Changed" : "In my packs";
            int fw = Draw.width(label) + 12;
            boolean sel = filter == f;
            Draw.rect(g, fx, y + 45, fw, 12, sel ? Theme.accent(0xD0) : 0xFF17171B);
            Draw.centered(g, label, fx + fw / 2, y + 47, sel ? Theme.onAccent() : 0xFFB0B0B8);
            hits.add(new Hit(fx, y + 45, fw, 12, () -> { filter = f; scroll = 0; }, null));
            fx += fw + 4;
        }

        int gx = x + 10, gy = y + 62, gw = w - 20, gh = h - 62 - 28;
        int tw = 82, th = 60, gap = 5, cols = Math.max(1, (gw + gap) / (tw + gap));
        tw = (gw - (cols - 1) * gap) / cols;
        List<Category> cats = categories();
        int rows = (cats.size() + cols - 1) / cols;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows * (th + gap) - gh));
        g.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int i = 0; i < cats.size(); i++) {
            int cx = gx + (i % cols) * (tw + gap), cy = gy + (i / cols) * (th + gap) - (int) scroll;
            if (cy + th < gy || cy > gy + gh) continue;
            tile(g, cats.get(i), cx, cy, tw, th, mx, my, gy, gh);
        }
        g.disableScissor();
        if (cats.isEmpty()) Draw.centered(g, PackMixer.sources().isEmpty() ? "No resource packs found - put some in your resourcepacks folder" : "Nothing matches",
                x + w / 2, gy + 30, 0xFF7E7E88);
        scrollbar(g, gx + gw + 2, gy, gh, rows * (th + gap));
    }

    private void tile(GuiGraphics g, Category c, int x, int y, int w, int h, int mx, int my, int areaY, int areaH) {
        String chosen = PackMixer.choice(c);
        int count = PackMixer.sourcesFor(c).size();
        boolean hov = Draw.inside(mx, my, x, y, w, h) && my >= areaY && my < areaY + areaH;
        Draw.rect(g, x, y, w, h, hov ? 0xFF1E1E24 : 0xFF141418);
        Draw.outline(g, x, y, w, h, chosen != null ? Theme.accent(0xC0) : hov ? 0x50FFFFFF : 0x1EFFFFFF);
        if (count > 0) Draw.text(g, String.valueOf(count), x + w - 4 - Draw.width(String.valueOf(count)), y + 4, 0xFF8E8E98);
        Preview pv = chosen != null ? preview(source(chosen), c) : null;
        int ix = x + w / 2 - 12, iy = y + 7;
        if (pv != null) g.blit(RenderPipelines.GUI_TEXTURED, pv.id(), ix, iy, 0, 0, 24, 24, pv.w(), Math.min(pv.h(), pv.w()), pv.w(), pv.h());
        else {
            g.pose().pushMatrix();
            g.pose().translate(ix, iy);
            g.pose().scale(1.5f, 1.5f);
            g.renderItem(new ItemStack(c.icon()), 0, 0);
            g.pose().popMatrix();
        }
        Draw.text(g, c.name(), x + 5, y + h - 21, 0xFFEDEDF2);
        String sub = chosen == null ? "Vanilla" : source(chosen) == null ? "(pack missing)" : source(chosen).displayName();
        Draw.text(g, Draw.trim(sub, w - 10), x + 5, y + h - 11, chosen != null ? Theme.accent() : 0xFF6A6A74);
        if (hov) tooltip = c.name() + "\n" + (count == 0 ? "None of your packs change this" : count + " pack" + (count == 1 ? "" : "s") + " you can pick")
                + (chosen != null ? "\nRight-click: back to vanilla" : "");
        int top = Math.max(y, areaY), bottom = Math.min(y + h, areaY + areaH);
        if (bottom > top) hits.add(new Hit(x, top, w, bottom - top,
                () -> { picking = c; scroll = 0; },
                () -> { if (PackMixer.choice(c) != null) { PackMixer.choose(c, null); mixChanged = true; note(c.name() + " is vanilla again"); } }));
    }

    private void renderPicker(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Category c = picking;
        button(g, mx, my, x + 10, y + 30, 46, 14, "‹ Back", false, () -> { picking = null; scroll = 0; });
        Draw.text(g, c.name(), x + 64, y + 33, 0xFFFFFFFF);
        String chosen = PackMixer.choice(c);

        // left: current
        int lx = x + 10, ly = y + 52, lw = 150, lh = h - 52 - 28;
        Draw.rect(g, lx, ly, lw, lh, 0xFF111115);
        Draw.outline(g, lx, ly, lw, lh, 0x20FFFFFF);
        Preview cur = chosen != null ? preview(source(chosen), c) : null;
        int big = Math.min(lw - 40, lh - 60);
        if (cur != null) g.blit(RenderPipelines.GUI_TEXTURED, cur.id(), lx + lw / 2 - big / 2, ly + 10, 0, 0, big, big, cur.w(), Math.min(cur.h(), cur.w()), cur.w(), cur.h());
        else {
            float s = big / 16f;
            g.pose().pushMatrix();
            g.pose().translate(lx + lw / 2f - big / 2f, ly + 10);
            g.pose().scale(s, s);
            g.renderItem(new ItemStack(c.icon()), 0, 0);
            g.pose().popMatrix();
        }
        Draw.text(g, "Now using", lx + 8, ly + big + 18, 0xFF7E7E88);
        Draw.text(g, Draw.trim(chosen == null ? "Vanilla" : source(chosen) == null ? "(pack missing)" : source(chosen).displayName(), lw - 16),
                lx + 8, ly + big + 29, chosen != null ? Theme.accent() : 0xFFEDEDF2);

        // right: packs that change it
        int gx = lx + lw + 8, gy = ly, gw = x + w - 10 - gx, gh = lh;
        List<Source> list = PackMixer.sourcesFor(c);
        int cw = 74, ch = 74, gap = 5, cols = Math.max(1, (gw + gap) / (cw + gap));
        cw = (gw - (cols - 1) * gap) / cols;
        int n = list.size() + 1, rows = (n + cols - 1) / cols;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows * (ch + gap) - gh));
        g.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int i = 0; i < n; i++) {
            int cx = gx + (i % cols) * (cw + gap), cy = gy + (i / cols) * (ch + gap) - (int) scroll;
            if (cy + ch < gy || cy > gy + gh) continue;
            Source s = i == 0 ? null : list.get(i - 1);
            boolean sel = s == null ? chosen == null : s.file.equals(chosen);
            boolean hov = Draw.inside(mx, my, cx, cy, cw, ch) && my >= gy && my < gy + gh;
            Draw.rect(g, cx, cy, cw, ch, hov ? 0xFF1E1E24 : 0xFF141418);
            Draw.outline(g, cx, cy, cw, ch, sel ? Theme.accent() : hov ? 0x50FFFFFF : 0x1EFFFFFF);
            int ps = Math.min(cw, ch) - 30;
            Preview pv = s == null ? null : preview(s, c);
            if (pv != null) g.blit(RenderPipelines.GUI_TEXTURED, pv.id(), cx + cw / 2 - ps / 2, cy + 6, 0, 0, ps, ps, pv.w(), Math.min(pv.h(), pv.w()), pv.w(), pv.h());
            else {
                float sc = ps / 16f;
                g.pose().pushMatrix();
                g.pose().translate(cx + cw / 2f - ps / 2f, cy + 6);
                g.pose().scale(sc, sc);
                g.renderItem(new ItemStack(c.icon()), 0, 0);
                g.pose().popMatrix();
            }
            String name = s == null ? "Vanilla" : s.displayName();
            Draw.centered(g, Draw.trim(name, cw - 8), cx + cw / 2, cy + ch - 20, 0xFFEDEDF2);
            if (sel) Draw.centered(g, "In use", cx + cw / 2, cy + ch - 10, Theme.accent());
            if (hov && s != null) tooltip = s.displayName() + "\nClick to use for " + c.name();
            int top = Math.max(cy, gy), bottom = Math.min(cy + ch, gy + gh);
            final Source fs = s;
            if (bottom > top) hits.add(new Hit(cx, top, cw, bottom - top,
                    () -> { PackMixer.choose(c, fs == null ? null : fs.file); mixChanged = true; note(c.name() + ": " + (fs == null ? "Vanilla" : fs.displayName())); },
                    () -> { PackMixer.choose(c, null); mixChanged = true; note(c.name() + ": Vanilla"); }));
        }
        g.disableScissor();
        if (list.isEmpty()) Draw.centered(g, "None of your packs change " + c.name(), gx + gw / 2, gy + ch + 20, 0xFF7E7E88);
        scrollbar(g, gx + gw + 2, gy, gh, rows * (ch + gap));
    }

    // ------------------------------------------------------------------ PACKS tab
    private boolean on(Pack p) { return enabled.contains(p.getId()); }

    private void renderPacks(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Draw.text(g, "Resource packs", x + 10, y + 31, 0xFFFFFFFF);
        Draw.text(g, "Top of the list wins", x + 92, y + 31, 0xFF7E7E88);
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Pack> list = new ArrayList<>();
        for (int i = enabled.size() - 1; i >= 0; i--) for (Pack p : all) if (p.getId().equals(enabled.get(i))) list.add(p);
        List<Pack> rest = new ArrayList<>();
        for (Pack p : all) if (!on(p)) rest.add(p);
        rest.sort((a, b) -> a.getTitle().getString().compareToIgnoreCase(b.getTitle().getString()));
        list.addAll(rest);
        if (!q.isEmpty()) list.removeIf(p -> !p.getTitle().getString().toLowerCase(Locale.ROOT).contains(q));

        int lx = x + 10, ly = y + 50, lw = w - 20, lh = h - 50 - 28, rowH = 26;
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() * rowH - lh));
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        int ry = ly - (int) scroll;
        for (Pack p : list) {
            if (ry + rowH >= ly && ry <= ly + lh) packRow(g, p, lx, ry, lw, rowH - 3, mx, my, ly, lh);
            ry += rowH;
        }
        g.disableScissor();
        scrollbar(g, lx + lw + 2, ly, lh, list.size() * rowH);
    }

    private void packRow(GuiGraphics g, Pack p, int x, int y, int w, int h, int mx, int my, int areaY, int areaH) {
        boolean on = on(p), fixed = p.isRequired();
        boolean hov = Draw.inside(mx, my, x, y, w, h) && my >= areaY && my < areaY + areaH;
        Draw.rect(g, x, y, w, h, hov ? 0xFF1C1C21 : 0xFF141418);
        Draw.outline(g, x, y, w, h, on ? Theme.accent(0x70) : 0x18FFFFFF);
        Draw.text(g, Draw.trim(p.getTitle().getString(), w - 130), x + 8, y + 4, on ? Theme.accent() : 0xFF9A9AA2);
        Draw.text(g, Draw.trim(p.getDescription().getString(), w - 130), x + 8, y + 13, 0xFF5E5E66);
        int tx = x + w - 30;
        if (fixed) { Draw.text(g, "Built-in", x + w - 8 - Draw.width("Built-in"), y + 8, 0xFF5E5E66); return; }
        Draw.toggle(g, tx, y + 7, on);
        clip(tx - 4, y, 30, h, areaY, areaH, () -> togglePack(p));
        if (on) {
            arrow(g, tx - 40, y + 5, "▲", mx, my, areaY, areaH, () -> move(p, 1));
            arrow(g, tx - 22, y + 5, "▼", mx, my, areaY, areaH, () -> move(p, -1));
        }
    }

    private void togglePack(Pack p) {
        if (on(p)) enabled.remove(p.getId()); else enabled.add(p.getId());
        listChanged = true;
        note("Press Apply to reload");
    }

    private void move(Pack p, int dir) {
        int i = enabled.indexOf(p.getId()), j = i + dir;
        if (i < 0 || j < 0 || j >= enabled.size()) return;
        Pack other = minecraft.getResourcePackRepository().getPack(enabled.get(j));
        if (other != null && other.isRequired()) return;
        enabled.set(i, enabled.get(j));
        enabled.set(j, p.getId());
        listChanged = true;
        note("Press Apply to reload");
    }

    // ------------------------------------------------------------------ apply
    private void applyAndClose() {
        if (!mixChanged && !listChanged) { minecraft.setScreen(parent); return; }
        PackRepository r = minecraft.getResourcePackRepository();
        try {
            String mixId = "file/" + PackMixer.MIX_NAME;
            if (mixChanged) {
                int files = PackMixer.build();
                r.reload();
                enabled.remove(mixId);
                if (PackMixer.changedCount() > 0 && r.isAvailable(mixId)) enabled.add(mixId);   // on top of everything
                RabbitsClient.LOG.info("Pack mixer: built {} with {} files", PackMixer.MIX_NAME, files);
            }
            r.setSelected(enabled);
            minecraft.options.updateResourcePacks(r);
        } catch (Exception e) {
            RabbitsClient.LOG.warn("Pack mixer: apply failed", e);
            note("Couldn't build the mix: " + e.getMessage());
            return;
        }
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ helpers
    private Source source(String file) {
        for (Source s : PackMixer.sources()) if (s.file.equals(file)) return s;
        return null;
    }

    private Preview preview(Source s, Category c) {
        if (s == null) return null;
        String path = s.previewPath(c);
        if (path == null) return null;
        String key = s.file + "|" + path;
        return PREVIEWS.computeIfAbsent(key, k -> {
            try {
                byte[] data = s.read(path);
                if (data == null) return Optional.empty();
                NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
                Identifier id = Identifier.fromNamespaceAndPath("therabbits", "packprev/" + Integer.toHexString(k.hashCode()));
                int w = img.getWidth(), h = img.getHeight();
                minecraft.getTextureManager().register(id, new DynamicTexture(() -> "therabbits pack preview", img));
                return Optional.of(new Preview(id, w, h));
            } catch (Exception e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private void note(String s) { status = s; statusAt = System.currentTimeMillis(); }

    private void scrollbar(GuiGraphics g, int x, int y, int h, int content) {
        if (content <= h) return;
        int bh = Math.max(14, h * h / content), by = y + (int) ((h - bh) * scroll / (content - h));
        g.fill(x, y, x + 2, y + h, 0x14FFFFFF);
        g.fill(x, by, x + 2, by + bh, Theme.accent(0xA0));
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, boolean primary, Runnable r) {
        boolean hov = Draw.inside(mx, my, x, y, w, h);
        int bg = primary ? Theme.accent() : hov ? 0xFF2A2A30 : 0xFF18181C;
        Draw.rect(g, x, y, w, h, hov && primary ? Theme.accent(0xD0) : bg);
        if (!primary) Draw.outline(g, x, y, w, h, 0x30FFFFFF);
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2, primary ? Theme.onAccent() : 0xFFD8D8DE);
        hits.add(new Hit(x, y, w, h, r, null));
    }

    private void iconButton(GuiGraphics g, int mx, int my, int x, int y, String icon, String tip, Runnable r) {
        boolean hov = Draw.inside(mx, my, x, y, 14, 14);
        Draw.rect(g, x, y, 14, 14, hov ? 0xFF2A2A30 : 0xFF18181C);
        Draw.centered(g, icon, x + 7, y + 3, 0xFFD8D8DE);
        if (hov) tooltip = tip;
        hits.add(new Hit(x, y, 14, 14, r, null));
    }

    private void arrow(GuiGraphics g, int x, int y, String s, int mx, int my, int areaY, int areaH, Runnable r) {
        boolean h = Draw.inside(mx, my, x, y, 14, 14);
        Draw.rect(g, x, y, 14, 14, h ? Theme.accent() : 0xFF1E1E22);
        Draw.centered(g, s, x + 7, y + 3, h ? Theme.onAccent() : 0xFFCFCFD4);
        clip(x, y, 14, 14, areaY, areaH, r);
    }

    private void clip(int x, int y, int w, int h, int areaY, int areaH, Runnable r) {
        int t = Math.max(y, areaY), b = Math.min(y + h, areaY + areaH);
        if (b > t) hits.add(new Hit(x, t, w, b - t, r, null));
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (search.visible && search.mouseClicked(e, dbl)) return true;
        search.setFocused(false);
        for (Hit h : List.copyOf(hits)) {
            if (!Draw.inside(e.x(), e.y(), h.x(), h.y(), h.w(), h.h())) continue;
            if (e.button() == 0 && h.left() != null) { h.left().run(); return true; }
            if (e.button() == 1 && h.right() != null) { h.right().run(); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - sy * 24);
        return true;
    }

    @Override
    public void onClose() {
        if (mixChanged || listChanged) applyAndClose(); else minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
