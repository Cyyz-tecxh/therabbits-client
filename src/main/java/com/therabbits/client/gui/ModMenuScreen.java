package com.therabbits.client.gui;

import com.therabbits.client.Config;
import com.therabbits.client.ModuleManager;
import com.therabbits.client.Presets;
import com.therabbits.client.hud.HudColors;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;
import com.therabbits.client.setting.NumberSetting;
import com.therabbits.client.setting.Setting;
import com.therabbits.client.setting.TextSetting;
import com.therabbits.client.setting.ActionSetting;
import com.therabbits.client.setting.InfoSetting;
import com.therabbits.client.setting.KeySetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Right Shift menu: header with logo + search, category pills, a grid of feature tiles.
 * Click a tile to toggle it, click its gear to open the settings page.
 */
public class ModMenuScreen extends Screen {
    private static final int TILE_H = 58, GAP = 6, HEADER = 40, TABS_H = 18, FOOTER = 26, SETTING_ROW = 20;

    private static final String[] ICON_HUD = {
            "#########", "#.......#", "#.##....#", "#.......#", "#.....#.#", "#....##.#", "#...###.#", "#.......#", "#########"};
    private static final String[] ICON_PVP = {
            "........#", ".......##", "......##.", ".#...##..", "..#.##...", "...##....", "..#.#....", ".#...#...", "#........"};
    private static final String[] ICON_VISUAL = {
            ".........", "...###...", ".##...##.", "#...#...#", "#..###..#", "#...#...#", ".##...##.", "...###...", "........."};
    private static final String[] ICON_UTILITY = {
            ".##...##.", "#..#.#..#", "#..#.#..#", ".##...##.", "...#.#...", "....#....", "...#.#...", "..#...#..", ".#.....#."};
    private static final String[] ICON_GEAR = {
            "...#.#...", ".#######.", ".##...##.", "##.....##", ".#.....#.", "##.....##", ".##...##.", ".#######.", "...#.#..."};
    private static final String[] ICON_ALL = {
            "###.###..", "###.###..", "###.###..", ".........", "###.###..", "###.###..", "###.###..", ".........", "........."};

    private final Screen parent;
    private Category filter;               // null = all
    private Module settingsFor;            // non-null = settings page
    private boolean presetsPage;
    private String toast;
    private long toastTime;
    private String search = "";
    private double scroll;
    private NumberSetting draggingSlider;
    private int sliderX, sliderW;
    private EditBox searchBox;
    private EditBox textEdit;
    private TextSetting editing;
    private KeySetting bindingKey;
    private final long opened = System.currentTimeMillis();
    private long pageChanged = opened;
    private long lastFrame = opened;
    private final Map<Module, Float> hoverAnim = new HashMap<>();
    private final Map<Module, Float> toggleAnim = new HashMap<>();

    private int W, H;
    private final List<Hit> hits = new ArrayList<>();
    private record Hit(int x, int y, int w, int h, Runnable action, Runnable right) {
        Hit(int x, int y, int w, int h, Runnable action) { this(x, y, w, h, action, null); }
    }
    private boolean favoritesOnly;
    private static final String[] ICON_STAR_SMALL = {"..#..", ".###.", "#####", ".###.", ".#.#."};

    public ModMenuScreen(Screen parent) {
        super(Component.literal("TheRabbits Menu"));
        this.parent = parent;
    }

    // ---------- test / external hooks ----------
    public void showPresets() { presetsPage = true; settingsFor = null; scroll = 0; pageChanged = System.currentTimeMillis(); }
    public void selectCategory(Category c) { presetsPage = false; filter = c; settingsFor = null; scroll = 0; pageChanged = System.currentTimeMillis(); }
    public void openSettings(Module m) { settingsFor = m; scroll = 0; pageChanged = System.currentTimeMillis(); }

    @Override
    protected void init() {
        W = Math.min(560, width - 16);
        H = Math.min(320, height - 16);
        int sw = Math.min(150, W / 3);
        searchBox = new EditBox(font, left() + W - sw - 12, top() + 14, sw - 8, 12, Component.literal("Search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(32);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setHint(Component.literal("Search features..."));
        searchBox.setValue(search);
        searchBox.setResponder(s -> { search = s; scroll = 0; });
        ModuleIcons.reload();
        textEdit = new EditBox(font, 0, 0, 100, 12, Component.literal("Text"));
        textEdit.setBordered(false);
        textEdit.setMaxLength(64);
        textEdit.setTextColor(0xFFFFFFFF);
        textEdit.visible = false;
        textEdit.setResponder(v -> { if (editing != null) editing.set(v); });
        addRenderableWidget(textEdit);
        addRenderableWidget(searchBox);
    }

    private int left() { return width / 2 - W / 2; }
    private int top() { return height / 2 - H / 2; }

    /** Animations are quick, and can be turned off (Client Settings -> Menu Animations). */
    private static float ease(float t) {
        if (!com.therabbits.client.module.utility.ClientSettingsModule.animations()) return 1;
        t = Mth.clamp(t * 1.8f, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level != null) super.renderBackground(g, mouseX, mouseY, delta);   // vanilla blur over the game
        else g.fillGradient(0, 0, width, height, 0xFF000000, 0xFF101010);
        float a = ease((System.currentTimeMillis() - opened) / 220f);
        g.fill(0, 0, width, height, ((int) (120 * a) << 24));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        // pop-in: scale up from 94% while fading in
        float open = ease((now - opened) / 240f);
        float sc = 0.94f + 0.06f * open;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, height / 2f);
        g.pose().scale(sc, sc);
        g.pose().translate(-width / 2f, -height / 2f);

        int x = left(), y = top();
        // panel with a soft drop shadow
        for (int i = 1; i <= 6; i++) g.fill(x - i, y - i + 3, x + W + i, y + H + i + 3, 0x0C000000);
        Draw.rect(g, x, y, W, H, 0xF00B0B0B);
        Draw.outline(g, x, y, W, H, 0x30FFFFFF);
        g.fill(x, y, x + W, y + 1, Theme.accent(0xC0));

        renderHeader(g, x, y, mouseX, mouseY);
        if (settingsFor == null) {
            renderTabs(g, x + 12, y + HEADER + 2, mouseX, mouseY);
            if (presetsPage) renderPresets(g, x + 12, y + HEADER + TABS_H + 10, W - 24, H - HEADER - TABS_H - 10 - FOOTER - 6, mouseX, mouseY, now);
            else renderGrid(g, x + 12, y + HEADER + TABS_H + 10, W - 24, H - HEADER - TABS_H - 10 - FOOTER - 6, mouseX, mouseY, dt);
        } else {
            renderSettingsPage(g, x + 12, y + HEADER + 2, W - 24, H - HEADER - FOOTER - 6, mouseX, mouseY, now);
        }
        renderFooter(g, x, y + H - FOOTER, mouseX, mouseY);

        g.pose().popMatrix();
        searchBox.visible = settingsFor == null && !presetsPage;
        if (settingsFor == null) { editing = null; textEdit.visible = false; }
        if (toast != null && now - toastTime < 2000) {
            int tw = Draw.width(toast) + 20;
            Draw.rect(g, width / 2 - tw / 2, top() - 22 + 0, tw, 16, 0xF0FFFFFF);
            Draw.centered(g, toast, width / 2, top() - 18, 0xFF000000);
        }
        super.render(g, mouseX, mouseY, delta);   // search box on top (not scaled; it sits in the header)
    }

    // ---------- header ----------
    private void renderHeader(GuiGraphics g, int x, int y, int mx, int my) {
        Draw.logo(g, x + 8, y + 5, 30);
        g.pose().pushMatrix();
        g.pose().translate(x + 42, y + 10);
        g.pose().scale(1.5f, 1.5f);
        Draw.text(g, "THE RABBITS", 0, 0, 0xFFFFFFFF);
        g.pose().popMatrix();
        Draw.text(g, "C L I E N T", x + 43, y + 25, 0xFF7A7A7A);

        if (settingsFor == null && !presetsPage) {
            int sw = Math.min(150, W / 3), sx = x + W - sw - 16, sy = y + 10;
            boolean focus = searchBox.isFocused();
            Draw.rect(g, sx, sy, sw, 18, 0xFF161616);
            Draw.outline(g, sx, sy, sw, 18, focus ? 0xC0FFFFFF : 0x40FFFFFF);
            hits.add(new Hit(sx, sy, sw, 18, () -> { setFocused(searchBox); searchBox.setFocused(true); }));
        }
        g.fill(x + 10, y + HEADER - 2, x + W - 10, y + HEADER - 1, 0x22FFFFFF);
    }

    // ---------- category pills ----------
    private void renderTabs(GuiGraphics g, int x, int y, int mx, int my) {
        Category[] cats = java.util.Arrays.stream(Category.values()).filter(c -> c != Category.COSMETICS).toArray(Category[]::new);
        int tx = x;
        for (int i = -1; i < cats.length; i++) {
            Category c = i < 0 ? null : cats[i];
            String label = c == null ? "All" : c.label();
            String[] icon = c == null ? ICON_ALL : icon(c);
            int w = Draw.width(label) + 28;
            boolean sel = !presetsPage && !favoritesOnly && filter == c;
            boolean hover = Draw.inside(mx, my, tx, y, w, TABS_H);
            Draw.rect(g, tx, y, w, TABS_H, sel ? Theme.accent() : hover ? 0xFF262626 : 0xFF151515);
            if (!sel) Draw.outline(g, tx, y, w, TABS_H, 0x30FFFFFF);
            pixelIcon(g, icon, tx + 7, y + 5, 1, sel ? Theme.onAccent() : 0xFFBBBBBB);
            Draw.text(g, label, tx + 20, y + 5, sel ? Theme.onAccent() : 0xFFD0D0D0);
            final Category fc = c;
            hits.add(new Hit(tx, y, w, TABS_H, () -> { favoritesOnly = false; selectCategory(fc); }));
            tx += w + 5;
        }
        {   // favourites
            String label = "Favorites";
            int w = Draw.width(label) + 24;
            boolean sel = !presetsPage && favoritesOnly;
            boolean hover = Draw.inside(mx, my, tx, y, w, TABS_H);
            Draw.rect(g, tx, y, w, TABS_H, sel ? Theme.accent() : hover ? 0xFF262626 : 0xFF151515);
            if (!sel) Draw.outline(g, tx, y, w, TABS_H, 0x30FFFFFF);
            pixelIcon(g, ICON_STAR_SMALL, tx + 7, y + 6, 1, sel ? Theme.onAccent() : 0xFFE8C34A);
            Draw.text(g, label, tx + 16, y + 5, sel ? Theme.onAccent() : 0xFFD0D0D0);
            hits.add(new Hit(tx, y, w, TABS_H, () -> { selectCategory(null); favoritesOnly = true; }));
        }
        // Presets pill (right side)
        String label = "Presets";
        int w = Draw.width(label) + 28, px = x + W - 24 - w;
        boolean hover = Draw.inside(mx, my, px, y, w, TABS_H);
        Draw.rect(g, px, y, w, TABS_H, presetsPage ? Theme.accent() : hover ? 0xFF262626 : 0xFF151515);
        if (!presetsPage) Draw.outline(g, px, y, w, TABS_H, 0x30FFFFFF);
        pixelIcon(g, ICON_STAR, px + 7, y + 5, 1, presetsPage ? Theme.onAccent() : 0xFFBBBBBB);
        Draw.text(g, label, px + 20, y + 5, presetsPage ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(px, y, w, TABS_H, this::showPresets));
    }

    private static final String[] ICON_STAR = {
            "....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..", ".##...##.", "#.......#"};

    // ---------- presets page ----------
    private void renderPresets(GuiGraphics g, int ax, int ay, int aw, int ah, int mx, int my, long now) {
        float slide = ease((now - pageChanged) / 200f);
        ay += (int) ((1 - slide) * 12);
        Draw.text(g, "BUILT-IN", ax, ay, 0xFF6A6A6A);
        int cols = 4, cw = (aw - (cols - 1) * GAP) / cols, ch = 62, cy = ay + 12;
        for (int i = 0; i < Presets.BUILT_IN.size(); i++) {
            Presets.BuiltIn p = Presets.BUILT_IN.get(i);
            int cx = ax + i * (cw + GAP);
            boolean active = Presets.active(p);
            boolean hover = Draw.inside(mx, my, cx, cy, cw, ch);
            Draw.rect(g, cx, cy, cw, ch, active ? Theme.accent() : hover ? 0xFF1E1E1E : 0xFF141414);
            Draw.outline(g, cx, cy, cw, ch, active ? Theme.accent() : hover ? 0x90FFFFFF : 0x28FFFFFF);
            int fg = active ? Theme.onAccent() : 0xFFFFFFFF, sub = active ? 0xFF404040 : 0xFF7A7A7A;
            Draw.text(g, p.name(), cx + 8, cy + 8, fg);
            String d = p.description();
            if (Draw.width(d) > cw - 14) d = Draw.trim(d, cw - 20 + 6);
            Draw.text(g, d, cx + 8, cy + 20, sub);
            Draw.text(g, Presets.count(p) + " features", cx + 8, cy + ch - 14, sub);
            Draw.text(g, active ? "ACTIVE" : "LOAD \u203A", cx + cw - 8 - Draw.width(active ? "ACTIVE" : "LOAD \u203A"), cy + ch - 14, fg);
            hits.add(new Hit(cx, cy, cw, ch, () -> { Presets.apply(p); toast("Loaded " + p.name()); }));
        }

        int sy = cy + ch + 16;
        Draw.text(g, "YOUR PRESETS  -  saves every toggle, setting and HUD position", ax, sy, 0xFF6A6A6A);
        int scw = (aw - (Presets.SLOTS - 1) * GAP) / Presets.SLOTS, sch = 58;
        sy += 12;
        for (int i = 0; i < Presets.SLOTS; i++) {
            final int slot = i;
            int cx = ax + i * (scw + GAP);
            boolean has = Presets.hasSlot(i);
            Draw.rect(g, cx, sy, scw, sch, 0xFF141414);
            Draw.outline(g, cx, sy, scw, sch, 0x28FFFFFF);
            Draw.text(g, "Custom " + (i + 1), cx + 8, sy + 8, 0xFFFFFFFF);
            Draw.text(g, Presets.slotInfo(i), cx + 8, sy + 20, 0xFF7A7A7A);
            int bw = (scw - 24) / 2, by = sy + sch - 22;
            // Save
            boolean hs = Draw.inside(mx, my, cx + 8, by, bw, 16);
            Draw.rect(g, cx + 8, by, bw, 16, hs ? Theme.accent() : 0xFF1C1C1C);
            Draw.outline(g, cx + 8, by, bw, 16, 0x40FFFFFF);
            Draw.centered(g, "Save", cx + 8 + bw / 2, by + 4, hs ? Theme.onAccent() : 0xFFD0D0D0);
            hits.add(new Hit(cx + 8, by, bw, 16, () -> { Presets.saveSlot(slot); toast("Saved to Custom " + (slot + 1)); }));
            // Load
            int lx = cx + 16 + bw;
            boolean hl = has && Draw.inside(mx, my, lx, by, bw, 16);
            Draw.rect(g, lx, by, bw, 16, !has ? 0xFF101010 : hl ? Theme.accent() : 0xFF1C1C1C);
            Draw.outline(g, lx, by, bw, 16, has ? 0x40FFFFFF : 0x18FFFFFF);
            Draw.centered(g, "Load", lx + bw / 2, by + 4, !has ? 0xFF4A4A4A : hl ? Theme.onAccent() : 0xFFD0D0D0);
            if (has) hits.add(new Hit(lx, by, bw, 16, () -> { Presets.loadSlot(slot); toast("Loaded Custom " + (slot + 1)); }));
        }
    }

    private void toast(String text) { toast = text; toastTime = System.currentTimeMillis(); }

    private static String[] icon(Category c) {
        return switch (c) {
            case HUD -> ICON_HUD;
            case COMBAT -> ICON_PVP;
            case VISUAL -> ICON_VISUAL;
            case UTILITY -> ICON_UTILITY;
            case COSMETICS -> ICON_STAR;
        };
    }

    // ---------- tile grid ----------
    private List<Module> visibleModules() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            if (m.category() == Category.COSMETICS) continue;   // cosmetics live in their own screen
            if (filter != null && m.category() != filter) continue;
            if (favoritesOnly && !Favorites.is(m.name())) continue;
            if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)
                    && !m.description().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    private void renderGrid(GuiGraphics g, int ax, int ay, int aw, int ah, int mx, int my, float dt) {
        List<Module> mods = visibleModules();
        int cols = Math.max(2, Math.min(4, (aw + GAP) / (120 + GAP)));
        int tw = (aw - (cols - 1) * GAP) / cols;
        int rows = (mods.size() + cols - 1) / cols;
        int content = rows * (TILE_H + GAP) - GAP;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - ah));

        g.enableScissor(ax, ay, ax + aw, ay + ah);
        if (mods.isEmpty()) Draw.centered(g, favoritesOnly ? "No favorites yet - click the star on any tile" : "No features match \"" + search + "\"", ax + aw / 2, ay + 30, 0xFF808080);
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int tx = ax + (i % cols) * (tw + GAP);
            int ty = ay + (i / cols) * (TILE_H + GAP) - (int) scroll;
            if (ty + TILE_H < ay || ty > ay + ah) continue;
            boolean inArea = my >= ay && my < ay + ah;
            boolean hover = inArea && Draw.inside(mx, my, tx, ty, tw, TILE_H);
            float hv = approach(hoverAnim, m, hover ? 1 : 0, dt * 8);
            float on = approach(toggleAnim, m, m.enabled() ? 1 : 0, dt * 9);
            int lift = (int) (hv * 2);
            ty -= lift;

            Draw.rect(g, tx, ty, tw, TILE_H, blend(0xFF141414, 0xFF1E1E1E, hv));
            Draw.outline(g, tx, ty, tw, TILE_H, hv > 0.05f ? Theme.accent((int) (0x28 + 0x90 * hv)) : 0x28FFFFFF);

            // picture badge
            int bx = tx + 8, by = ty + 8;
            Draw.rect(g, bx, by, 22, 22, blend(0xFF1A1A1A, 0xFF2C2C2C, on));
            Draw.outline(g, bx, by, 22, 22, Theme.accent((int) (0x30 + 0xA0 * on)));
            ModuleIcons.draw(g, m.name(), bx + 3, by + 3, 16);
            if (on < 0.99f) Draw.rect(g, bx + 1, by + 1, 20, 20, ((int) (150 * (1 - on)) << 24) | 0x0B0B0B);

            int textX = bx + 28;
            Draw.text(g, m.name(), textX, by + 2, blend(0xFF9A9A9A, 0xFFFFFFFF, on));
            String d = m.description();
            int maxW = tw - (textX - tx) - 8;
            if (Draw.width(d) > maxW) d = Draw.trim(d, maxW - 6 + 6);
            Draw.text(g, d, textX, by + 13, 0xFF6A6A6A);

            // status bar along the bottom
            int sy = ty + TILE_H - 16;
            Draw.text(g, m.enabled() ? "ENABLED" : "DISABLED", tx + 8, sy + 4, blend(0xFF5A5A5A, 0xFFFFFFFF, on));
            int barX = tx + 8, barW = tw - 16 - (m.settings().isEmpty() ? 0 : 16);
            g.fill(barX, sy + 13, barX + barW, sy + 14, 0x30FFFFFF);
            g.fill(barX, sy + 13, barX + (int) (barW * on), sy + 14, Theme.accent());

            final int ftx = tx, fty = ty;
            if (!m.settings().isEmpty()) {
                int gx = tx + tw - 18, gy = sy + 2;
                boolean gh = inArea && Draw.inside(mx, my, gx - 2, gy - 2, 14, 14);
                pixelIcon(g, ICON_GEAR, gx, gy, 1, gh ? Theme.accent() : 0xFF8A8A8A);
                if (inArea) hits.add(new Hit(gx - 3, gy - 3, 15, 15, () -> openSettings(m)));
            }
            {   // favourite star (top-right)
                int fx = tx + tw - 12, fy = ty + 5;
                boolean fav = Favorites.is(m.name());
                boolean fh = inArea && Draw.inside(mx, my, fx - 2, fy - 2, 9, 9);
                if (fav || hover) pixelIcon(g, ICON_STAR_SMALL, fx, fy, 1, fav ? 0xFFE8C34A : fh ? Theme.accent() : 0xFF5A5A5A);
                if (inArea) hits.add(new Hit(fx - 2, fy - 2, 9, 9, () -> { Favorites.toggle(m.name()); toast(Favorites.is(m.name()) ? "Added to favorites" : "Removed from favorites"); }));
            }
            if (inArea) hits.add(new Hit(ftx, fty, tw, TILE_H, m::toggle, m.settings().isEmpty() ? null : () -> openSettings(m)));
        }
        g.disableScissor();

        if (content > ah) {   // scrollbar
            int bh = Math.max(20, ah * ah / content);
            int by = ay + (int) ((ah - bh) * (scroll / (content - ah)));
            g.fill(ax + aw + 3, ay, ax + aw + 4, ay + ah, 0x20FFFFFF);
            g.fill(ax + aw + 2, by, ax + aw + 5, by + bh, 0x90FFFFFF);
        }
    }

    private static float approach(Map<Module, Float> map, Module m, float target, float speed) {
        float v = map.getOrDefault(m, target);
        v += Mth.clamp(target - v, -speed, speed);
        map.put(m, v);
        return v;
    }

    private static int blend(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
            r |= ((int) (ca + (cb - ca) * t) & 0xFF) << s;
        }
        return r;
    }

    private static void pixelIcon(GuiGraphics g, String[] rows, int x, int y, int px, int color) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++)
                if (rows[r].charAt(c) == '#') g.fill(x + c * px, y + r * px, x + c * px + px, y + r * px + px, color);
    }

    // ---------- settings page ----------
    private void renderSettingsPage(GuiGraphics g, int ax, int ay, int aw, int ah, int mx, int my, long now) {
        Module m = settingsFor;
        float slide = ease((now - pageChanged) / 200f);
        int off = (int) ((1 - slide) * 30);
        ax += off;

        boolean backHover = Draw.inside(mx, my, ax, ay, 54, 18);
        Draw.rect(g, ax, ay, 54, 18, backHover ? Theme.accent() : 0xFF151515);
        Draw.outline(g, ax, ay, 54, 18, 0x40FFFFFF);
        Draw.text(g, "‹ Back", ax + 9, ay + 5, backHover ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(ax, ay, 54, 18, () -> { settingsFor = null; pageChanged = System.currentTimeMillis(); }));

        ModuleIcons.draw(g, m.name(), ax + 66, ay, 18);
        Draw.text(g, m.name(), ax + 90, ay + 1, 0xFFFFFFFF);
        Draw.text(g, m.description(), ax + 90, ay + 11, 0xFF7A7A7A);
        int tgx = ax + aw - 30 - off;
        Draw.toggle(g, tgx, ay + 4, m.enabled());
        hits.add(new Hit(tgx - 4, ay, 30, 18, m::toggle));

        int listY = ay + 28, listH = ah - 28;
        g.fill(ax, listY - 4, ax + aw - off, listY - 3, 0x22FFFFFF);
        int content = m.settings().size() * SETTING_ROW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - listH));
        g.enableScissor(ax, listY, ax + aw, listY + listH);
        renderSettings(g, m, ax, listY - (int) scroll, aw - off, mx, my, listY, listH);
        g.disableScissor();
    }

    private void renderSettings(GuiGraphics g, Module m, int x, int y, int w, int mx, int my, int areaY, int areaH) {
        for (Setting<?> s : m.settings()) {
            boolean rowHover = Draw.inside(mx, my, x, y, w, SETTING_ROW - 2) && my >= areaY && my < areaY + areaH;
            Draw.rect(g, x, y, w, SETTING_ROW - 2, rowHover ? 0xFF181818 : 0xFF121212);
            Draw.text(g, s.name(), x + 8, y + 6, 0xFFD0D0D0);
            int cy = y + (SETTING_ROW - 2) / 2;
            if (s instanceof BoolSetting b) {
                int tx = x + w - 28;
                Draw.toggle(g, tx, cy - 5, b.on());
                addClipped(tx - 4, y, 30, SETTING_ROW, areaY, areaH, b::toggle);
            } else if (s instanceof ModeSetting ms) {
                int bw = 120, bx = x + w - bw - 8;
                boolean hover = Draw.inside(mx, my, bx, y + 2, bw, SETTING_ROW - 6);
                Draw.rect(g, bx, y + 2, bw, SETTING_ROW - 6, hover ? 0xFF262626 : 0xFF0C0C0C);
                Draw.outline(g, bx, y + 2, bw, SETTING_ROW - 6, 0x40FFFFFF);
                Draw.text(g, "‹", bx + 4, y + 6, 0xFF8A8A8A);
                Draw.text(g, "›", bx + bw - 8, y + 6, 0xFF8A8A8A);
                String label = ms.display();
                int lx = bx + bw / 2 - Draw.width(label) / 2;
                if (ms.isColor() && !label.equals("Global")) {
                    int idx = java.util.Arrays.asList(HudColors.NAMES).indexOf(label);
                    int sw = 0xFF000000 | HudColors.rgb(idx, 0);
                    lx += 5;
                    Draw.rect(g, lx - 11, y + 6, 7, 7, sw);
                    Draw.text(g, label, lx, y + 6, sw);
                } else {
                    Draw.text(g, label, lx, y + 6, 0xFFFFFFFF);
                }
                addClipped(bx, y, bw / 2, SETTING_ROW, areaY, areaH, ms::previous);
                addClipped(bx + bw / 2, y, bw - bw / 2, SETTING_ROW, areaY, areaH, ms::next);
            } else if (s instanceof TextSetting ts) {
                int bw = Math.min(200, w / 2), bx = x + w - bw - 8;
                boolean active = editing == ts;
                Draw.rect(g, bx, y + 2, bw, SETTING_ROW - 6, 0xFF0C0C0C);
                Draw.outline(g, bx, y + 2, bw, SETTING_ROW - 6, active ? 0xC0FFFFFF : 0x40FFFFFF);
                if (active) {
                    textEdit.setX(bx + 4); textEdit.setY(y + 5); textEdit.setWidth(bw - 8);
                    textEdit.visible = y >= areaY && y + SETTING_ROW <= areaY + areaH;
                } else {
                    String v = ts.get();
                    if (Draw.width(v) > bw - 10) v = Draw.trim(v, bw - 16 + 6);
                    Draw.text(g, v.isEmpty() ? "click to type" : v, bx + 4, y + 6, v.isEmpty() ? 0xFF5A5A5A : 0xFFFFFFFF);
                }
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, () -> {
                    editing = ts;
                    textEdit.setMaxLength(ts.maxLength());
                    textEdit.setValue(ts.get());
                    textEdit.visible = true;
                    setFocused(textEdit);
                    textEdit.setFocused(true);
                });
            } else if (s instanceof ActionSetting as) {
                int bw = 90, bx = x + w - bw - 8;
                boolean hover = Draw.inside(mx, my, bx, y + 2, bw, SETTING_ROW - 6);
                Draw.rect(g, bx, y + 2, bw, SETTING_ROW - 6, hover ? Theme.accent() : 0xFF1C1C1C);
                Draw.outline(g, bx, y + 2, bw, SETTING_ROW - 6, 0x40FFFFFF);
                Draw.centered(g, as.get(), bx + bw / 2, y + 6, hover ? Theme.onAccent() : 0xFFD0D0D0);
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, as::run);
            } else if (s instanceof KeySetting ks) {
                int bw = 90, bx = x + w - bw - 8;
                boolean waiting = bindingKey == ks;
                boolean hover = Draw.inside(mx, my, bx, y + 2, bw, SETTING_ROW - 6);
                Draw.rect(g, bx, y + 2, bw, SETTING_ROW - 6, waiting ? Theme.accent() : hover ? 0xFF262626 : 0xFF0C0C0C);
                Draw.outline(g, bx, y + 2, bw, SETTING_ROW - 6, 0x40FFFFFF);
                Draw.centered(g, waiting ? "Press a key..." : ks.display(), bx + bw / 2, y + 6, waiting ? Theme.onAccent() : 0xFFFFFFFF);
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, () -> bindingKey = ks);
            } else if (s instanceof InfoSetting in) {
                String v = in.text();
                int maxW = w - Draw.width(s.name()) - 28;
                if (Draw.width(v) > maxW) v = Draw.trim(v, maxW);
                Draw.text(g, v, x + w - 8 - Draw.width(v), y + 6, 0xFFB8B8B8);
            } else if (s instanceof NumberSetting n) {
                int sw = 140, sx = x + w - sw - 44;
                Draw.rect(g, sx, cy - 1, sw, 2, 0xFF3A3A3A);
                int kx = sx + (int) (n.fraction() * sw);
                Draw.rect(g, sx, cy - 1, kx - sx, 2, 0xFFFFFFFF);
                Draw.rect(g, kx - 3, cy - 4, 7, 8, 0xFFFFFFFF);
                String v = n.display();
                Draw.text(g, v, x + w - 8 - Draw.width(v), y + 6, 0xFFFFFFFF);
                addClipped(sx - 4, y, sw + 8, SETTING_ROW, areaY, areaH, () -> {
                    draggingSlider = n; sliderX = sx; sliderW = sw;
                });
            }
            y += SETTING_ROW;
        }
    }

    private void addClipped(int x, int y, int w, int h, int areaY, int areaH, Runnable r) {
        int top = Math.max(y, areaY), bottom = Math.min(y + h, areaY + areaH);
        if (bottom > top) hits.add(new Hit(x, top, w, bottom - top, r));
    }

    // ---------- footer ----------
    private void renderFooter(GuiGraphics g, int x, int y, int mx, int my) {
        g.fill(x + 10, y, x + W - 10, y + 1, 0x22FFFFFF);
        int bw = 70, bx = x + 12, by = y + 5;
        boolean hover = Draw.inside(mx, my, bx, by, bw, 16);
        Draw.rect(g, bx, by, bw, 16, hover ? Theme.accent() : 0xFF151515);
        Draw.outline(g, bx, by, bw, 16, 0x40FFFFFF);
        Draw.centered(g, "Edit HUD", bx + bw / 2, by + 4, hover ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(bx, by, bw, 16, () -> minecraft.setScreen(new HudEditorScreen(this))));
        int cbx = bx + bw + 6, cbw = 76;
        boolean ch = Draw.inside(mx, my, cbx, by, cbw, 16);
        Draw.rect(g, cbx, by, cbw, 16, ch ? Theme.accent() : 0xFF151515);
        Draw.outline(g, cbx, by, cbw, 16, 0x40FFFFFF);
        Draw.centered(g, "Cosmetics", cbx + cbw / 2, by + 4, ch ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(cbx, by, cbw, 16, () -> minecraft.setScreen(new CosmeticsScreen(parent))));
        int pbx = cbx + cbw + 6, pbw = 52;
        boolean ph = Draw.inside(mx, my, pbx, by, pbw, 16);
        Draw.rect(g, pbx, by, pbw, 16, ph ? Theme.accent() : 0xFF151515);
        Draw.outline(g, pbx, by, pbw, 16, 0x40FFFFFF);
        Draw.centered(g, "Packs", pbx + pbw / 2, by + 4, ph ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(pbx, by, pbw, 16, () -> minecraft.setScreen(new PackCustomizerScreen(this))));

        long on = ModuleManager.all().stream().filter(Module::enabled).count();
        String info = on + " / " + ModuleManager.all().size() + " enabled";
        Draw.text(g, info, x + W - 12 - Draw.width(info), by + 4, 0xFF7A7A7A);
        Draw.centered(g, settingsFor == null ? "Right-click: settings" : "Right Shift", x + W / 2, by + 4, 0xFF5A5A5A);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            for (Hit h : List.copyOf(hits))
                if (h.right() != null && Draw.inside(event.x(), event.y(), h.x(), h.y(), h.w(), h.h())) { h.right().run(); return true; }
        }
        if (event.button() == 0) {
            if (settingsFor == null && searchBox.mouseClicked(event, doubleClick)) return true;
            if (editing != null && textEdit.visible && textEdit.mouseClicked(event, doubleClick)) return true;
            if (editing != null) { editing = null; textEdit.visible = false; textEdit.setFocused(false); }
            double mx = event.x(), my = event.y();
            // first match wins: gear buttons are registered before the tile under them
            for (Hit h : List.copyOf(hits)) {
                if (Draw.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                    h.action().run();
                    if (draggingSlider != null) updateSlider(mx);
                    return true;
                }
            }
            searchBox.setFocused(false);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (bindingKey != null) {
            bindingKey.bind(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE ? -1 : event.key());
            bindingKey = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingSlider != null) {
            updateSlider(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    private void updateSlider(double mouseX) {
        draggingSlider.setFraction((mouseX - sliderX) / sliderW);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingSlider = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - scrollY * 20);
        return true;
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
