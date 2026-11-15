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
import com.therabbits.client.setting.ColorSetting;
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
    // easier editing: dropdowns, colour picker, typed numbers, scroll on sliders
    private ModeSetting openMode;
    private int modeX, modeY, modeW;
    private double modeScroll;
    private ColorSetting openColor;
    private int colX, colY;
    private int dragColor;                       // 0 none, 1 saturation/brightness square, 2 hue bar
    private final float[] hsv = new float[3];
    private EditBox hexEdit, numEdit;
    private NumberSetting editingNum;
    private final List<Hit> popupHits = new ArrayList<>();
    private int popX, popY, popW, popH;
    private record SliderArea(int x, int y, int w, int h, NumberSetting n) {}
    private final List<SliderArea> sliderAreas = new ArrayList<>();
    private static final String[] ICON_STAR_SMALL = {"..#..", ".###.", "#####", ".###.", ".#.#."};

    public ModMenuScreen(Screen parent) {
        super(Component.literal("TheRabbits Menu"));
        this.parent = parent;
    }

    // ---------- test / external hooks ----------
    public void showPresets() { presetsPage = true; settingsFor = null; scroll = 0; pageChanged = System.currentTimeMillis(); }
    public void selectCategory(Category c) { presetsPage = false; filter = c; settingsFor = null; scroll = 0; pageChanged = System.currentTimeMillis(); }
    public void openSettings(Module m) { settingsFor = m; scroll = 0; pageChanged = System.currentTimeMillis(); }
    private String pendingOpen;
    /** Test hook: open the dropdown / colour picker of a setting on the current settings page. */
    public void openSetting(String name) { pendingOpen = name; }

    @Override
    protected void init() {
        W = Math.min(560, width - 16);
        H = Math.min(320, height - 16);
        int sw = Math.min(150, W / 3);
        searchBox = new EditBox(font, left() + W - sw - 2, top() + 15, sw - 18, 12, Component.literal("Search"));
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
        hexEdit = new EditBox(font, 0, 0, 56, 12, Component.literal("Hex"));
        hexEdit.setBordered(false);
        hexEdit.setMaxLength(7);
        hexEdit.setTextColor(0xFFFFFFFF);
        hexEdit.visible = false;
        hexEdit.setResponder(v -> { if (openColor != null && hexEdit.isFocused() && openColor.setHex(v)) syncHsv(); });
        addRenderableWidget(hexEdit);
        numEdit = new EditBox(font, 0, 0, 40, 12, Component.literal("Value"));
        numEdit.setBordered(false);
        numEdit.setMaxLength(10);
        numEdit.setTextColor(0xFFFFFFFF);
        numEdit.visible = false;
        addRenderableWidget(numEdit);
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
        popupHits.clear();
        sliderAreas.clear();

        // pop-in: scale up from 94% while fading in
        float open = ease((now - opened) / 240f);
        float sc = 0.94f + 0.06f * open;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, height / 2f);
        g.pose().scale(sc, sc);
        g.pose().translate(-width / 2f, -height / 2f);

        int x = left(), y = top();
        // panel with a soft drop shadow
        for (int i = 1; i <= 10; i++) Draw.rrect(g, x - i, y - i + 4, W + 2 * i, H + 2 * i, 10 + i, 0x07000000);
        Draw.glow(g, x, y, W, H, 10, Theme.accent(0x90), 4);
        Draw.rgrad(g, x, y, W, H, 10, 0xF8141418, 0xF808080A);
        Draw.routline(g, x, y, W, H, 10, 0x2EFFFFFF);
        // accent light along the top edge, fading out to the sides
        for (int i = 0; i < W - 40; i++) {
            float f = 1f - Math.abs(i - (W - 40) / 2f) / ((W - 40) / 2f);
            g.fill(x + 20 + i, y, x + 21 + i, y + 1, Theme.accent((int) (0xE0 * f)));
            g.fill(x + 20 + i, y + 1, x + 21 + i, y + 2, Theme.accent((int) (0x40 * f)));
        }

        renderHeader(g, x, y, mouseX, mouseY);
        if (settingsFor == null) {
            renderTabs(g, x + 12, y + HEADER + 2, mouseX, mouseY);
            if (presetsPage) renderPresets(g, x + 12, y + HEADER + TABS_H + 10, W - 24, H - HEADER - TABS_H - 10 - FOOTER - 6, mouseX, mouseY, now);
            else renderGrid(g, x + 12, y + HEADER + TABS_H + 10, W - 24, H - HEADER - TABS_H - 10 - FOOTER - 6, mouseX, mouseY, dt);
        } else {
            renderSettingsPage(g, x + 12, y + HEADER + 2, W - 24, H - HEADER - FOOTER - 6, mouseX, mouseY, now);
        }
        renderFooter(g, x, y + H - FOOTER, mouseX, mouseY);
        if (settingsFor == null) { openMode = null; openColor = null; editingNum = null; }
        renderPopups(g, mouseX, mouseY);
        hexEdit.visible = openColor != null;
        numEdit.visible = editingNum != null;

        g.pose().popMatrix();
        searchBox.visible = settingsFor == null && !presetsPage;
        if (settingsFor == null) { editing = null; textEdit.visible = false; }
        if (toast != null && now - toastTime < 2000) {
            int tw = Draw.width(toast) + 20;
            Draw.glow(g, width / 2 - tw / 2, top() - 22, tw, 16, 8, Theme.accent(), 3);
            Draw.rgrad(g, width / 2 - tw / 2, top() - 22, tw, 16, 8, Theme.accent(), Draw.shade(Theme.accent(), 0.8f));
            Draw.centered(g, toast, width / 2, top() - 18, Theme.onAccent());
        }
        super.render(g, mouseX, mouseY, delta);   // search box on top (not scaled; it sits in the header)
    }

    // ---------- header ----------
    private void renderHeader(GuiGraphics g, int x, int y, int mx, int my) {
        Draw.glow(g, x + 8, y + 5, 30, 30, 8, Theme.accent(0x80), 3);
        Draw.logo(g, x + 8, y + 5, 30);
        g.pose().pushMatrix();
        g.pose().translate(x + 42, y + 10);
        g.pose().scale(1.5f, 1.5f);
        Draw.text(g, "THE RABBITS", 0, 0, 0xFFFFFFFF);
        g.pose().popMatrix();
        Draw.text(g, "C L I E N T", x + 43, y + 25, Theme.accent(0xB0));

        if (settingsFor == null && !presetsPage) {
            int sw = Math.min(150, W / 3), sx = x + W - sw - 16, sy = y + 10;
            boolean focus = searchBox.isFocused();
            if (focus) Draw.glow(g, sx, sy, sw, 18, 9, Theme.accent(), 3);
            Draw.rgrad(g, sx, sy, sw, 18, 9, 0xFF1C1C21, 0xFF141418);
            Draw.routline(g, sx, sy, sw, 18, 9, focus ? Theme.accent(0xD0) : 0x38FFFFFF);
            // magnifier
            Draw.routline(g, sx + 6, sy + 5, 6, 6, 3, 0xFF8C8C94);
            g.fill(sx + 11, sy + 10, sx + 13, sy + 12, 0xFF8C8C94);
            hits.add(new Hit(sx, sy, sw, 18, () -> { setFocused(searchBox); searchBox.setFocused(true); }));
        }
        g.fillGradient(x + 10, y + HEADER - 2, x + W - 10, y + HEADER - 1, 0x22FFFFFF, 0x08FFFFFF);
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
            pill(g, tx, y, w, sel, hover);
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
            pill(g, tx, y, w, sel, hover);
            pixelIcon(g, ICON_STAR_SMALL, tx + 7, y + 6, 1, sel ? Theme.onAccent() : 0xFFE8C34A);
            Draw.text(g, label, tx + 16, y + 5, sel ? Theme.onAccent() : 0xFFD0D0D0);
            hits.add(new Hit(tx, y, w, TABS_H, () -> { selectCategory(null); favoritesOnly = true; }));
        }
        // Presets pill (right side)
        String label = "Presets";
        int w = Draw.width(label) + 28, px = x + W - 24 - w;
        boolean hover = Draw.inside(mx, my, px, y, w, TABS_H);
        pill(g, px, y, w, presetsPage, hover);
        pixelIcon(g, ICON_STAR, px + 7, y + 5, 1, presetsPage ? Theme.onAccent() : 0xFFBBBBBB);
        Draw.text(g, label, px + 20, y + 5, presetsPage ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(px, y, w, TABS_H, this::showPresets));
    }

    /** Rounded category pill: accent gradient + glow when selected. */
    private static void pill(GuiGraphics g, int x, int y, int w, boolean sel, boolean hover) {
        if (sel) {
            Draw.glow(g, x, y, w, TABS_H, TABS_H / 2, Theme.accent(), 3);
            Draw.rgrad(g, x, y, w, TABS_H, TABS_H / 2, Theme.accent(), Draw.shade(Theme.accent(), 0.78f));
        } else {
            Draw.rgrad(g, x, y, w, TABS_H, TABS_H / 2, hover ? 0xFF2A2A30 : 0xFF1A1A1E, hover ? 0xFF202025 : 0xFF131316);
            Draw.routline(g, x, y, w, TABS_H, TABS_H / 2, hover ? Theme.accent(0x80) : 0x2AFFFFFF);
        }
    }

    // ---------- popups: dropdown list and colour picker ----------
    private static final int[] PRESETS = {0xFFFFFF, 0xD0D0D0, 0x909090, 0x202020, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55,
            0x00C040, 0x55FFFF, 0x6EC6FF, 0x5577FF, 0xAA55FF, 0xFF77DD};

    private void syncHsv() {
        if (openColor == null) return;
        int c = openColor.rgb();
        java.awt.Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, hsv);
        if (!hexEdit.isFocused()) hexEdit.setValue(openColor.hex());
    }

    private void applyHsv() {
        if (openColor == null) return;
        openColor.setRgb(java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]));
        hexEdit.setValue(openColor.hex());
    }

    private void applyNum() {
        if (editingNum == null) return;
        try { editingNum.set(Double.parseDouble(numEdit.getValue().trim().replace(',', '.'))); } catch (NumberFormatException ignored) {}
        editingNum = null;
        numEdit.setFocused(false);
    }

    private void popupPanel(GuiGraphics g, int x, int y, int w, int h) {
        popX = x; popY = y; popW = w; popH = h;
        for (int i = 1; i <= 6; i++) Draw.rrect(g, x - i, y - i + 3, w + 2 * i, h + 2 * i, 8 + i, 0x0C000000);
        Draw.glow(g, x, y, w, h, 8, Theme.accent(0xA0), 3);
        Draw.rgrad(g, x, y, w, h, 8, 0xFC1C1C22, 0xFC111114);
        Draw.routline(g, x, y, w, h, 8, Theme.accent(0x70));
    }

    private void renderPopups(GuiGraphics g, int mx, int my) {
        popW = 0;
        if (openMode != null) {
            ModeSetting ms = openMode;
            int n = ms.count(), vis = Math.min(n, 9), ih = 14;
            int h = vis * ih + 6, x = modeX, y = modeY + 17;
            if (y + h > height - 4) y = modeY - h - 3;
            modeScroll = Mth.clamp(modeScroll, 0, Math.max(0, n - vis));
            popupPanel(g, x, y, modeW, h);
            int first = (int) modeScroll;
            for (int k = 0; k < vis; k++) {
                int i = first + k, iy = y + 3 + k * ih;
                boolean sel = ms.get() == i, hov = Draw.inside(mx, my, x + 3, iy, modeW - 6, ih);
                if (sel) Draw.rgrad(g, x + 3, iy, modeW - 6, ih, 5, Theme.accent(), Draw.shade(Theme.accent(), 0.8f));
                else if (hov) Draw.rrect(g, x + 3, iy, modeW - 6, ih, 5, 0x30FFFFFF);
                String lbl = ms.label(i);
                int tx = x + 9;
                if (ms.isColor() && !lbl.equals("Global")) {
                    int idx = java.util.Arrays.asList(HudColors.NAMES).indexOf(lbl);
                    if (idx >= 0) { Draw.rrect(g, tx, iy + 4, 7, 7, 3, 0xFF000000 | HudColors.rgb(idx, 0)); tx += 11; }
                }
                Draw.text(g, Draw.trim(lbl, modeW - (tx - x) - 6), tx, iy + 3, sel ? Theme.onAccent() : 0xFFE0E0E6);
                final int fi = i;
                popupHits.add(new Hit(x + 3, iy, modeW - 6, ih, () -> { ms.set(fi); openMode = null; }));
            }
            if (n > vis) {   // scroll hint
                int bh = Math.max(10, h * vis / n), by = y + (int) ((h - bh) * (modeScroll / (n - vis)));
                g.fill(x + modeW - 3, by, x + modeW - 2, by + bh, Theme.accent(0xA0));
            }
        }
        if (openColor != null) {
            ColorSetting cs = openColor;
            int w = 176, h = 124, x = Math.min(colX + 120 - w, width - w - 6), y = colY + 17;
            if (y + h > height - 4) y = colY - h - 3;
            popupPanel(g, x, y, w, h);
            // saturation / brightness square (2px cells)
            int sx = x + 8, sy = y + 8, ss = 80;
            for (int i = 0; i < ss; i += 2)
                for (int j = 0; j < ss; j += 2)
                    g.fill(sx + i, sy + j, sx + i + 2, sy + j + 2, 0xFF000000 | java.awt.Color.HSBtoRGB(hsv[0], i / (float) ss, 1f - j / (float) ss));
            Draw.routline(g, sx - 1, sy - 1, ss + 2, ss + 2, 2, 0x40FFFFFF);
            int kx = sx + (int) (hsv[1] * ss), ky = sy + (int) ((1 - hsv[2]) * ss);
            Draw.routline(g, kx - 3, ky - 3, 7, 7, 3, 0xFFFFFFFF);
            // hue bar
            int hx = sx + ss + 6, hw = 10;
            for (int j = 0; j < ss; j++) g.fill(hx, sy + j, hx + hw, sy + j + 1, 0xFF000000 | java.awt.Color.HSBtoRGB(j / (float) ss, 1f, 1f));
            int hy = sy + (int) (hsv[0] * ss);
            g.fill(hx - 2, hy - 1, hx + hw + 2, hy + 1, 0xFFFFFFFF);
            // right column: preview + hex + options
            int rx = hx + hw + 8, rw = x + w - 8 - rx;
            int cur = 0xFF000000 | cs.color(0);
            Draw.glow(g, rx, sy, rw, 14, 5, cur, 2);
            Draw.rrect(g, rx, sy, rw, 14, 5, cur);
            field(g, rx, sy + 18, rw, 14, false, hexEdit.isFocused());
            hexEdit.setX(rx + 5); hexEdit.setY(sy + 21); hexEdit.setWidth(rw - 8);
            int by = sy + 36;
            if (cs.allowGlobal()) {
                boolean gh = Draw.inside(mx, my, rx, by, rw, 14);
                chip(g, rx, by, rw, "Global", cs.global(), gh);
                popupHits.add(new Hit(rx, by, rw, 14, () -> cs.setGlobal(!cs.global())));
                by += 17;
            }
            boolean ch = Draw.inside(mx, my, rx, by, rw, 14);
            chip(g, rx, by, rw, "Chroma", cs.chroma(), ch);
            popupHits.add(new Hit(rx, by, rw, 14, () -> cs.setChroma(!cs.chroma())));
            by += 17;
            boolean rh = Draw.inside(mx, my, rx, by, rw, 14);
            chip(g, rx, by, rw, "Reset", false, rh);
            popupHits.add(new Hit(rx, by, rw, 14, () -> { cs.reset(); syncHsv(); }));
            // preset swatches
            int py = sy + ss + 8, pw = (w - 16 - (PRESETS.length - 1) * 2) / PRESETS.length;
            for (int k = 0; k < PRESETS.length; k++) {
                int px = x + 8 + k * (pw + 2), c = PRESETS[k];
                boolean sel = !cs.global() && !cs.chroma() && cs.rgb() == c;
                if (sel) Draw.glow(g, px, py, pw, pw, 3, 0xFF000000 | c, 2);
                Draw.rrect(g, px, py, pw, pw, 3, 0xFF000000 | c);
                if (sel || Draw.inside(mx, my, px, py, pw, pw)) Draw.routline(g, px - 1, py - 1, pw + 2, pw + 2, 3, 0xFFFFFFFF);
                popupHits.add(new Hit(px, py, pw, pw, () -> { cs.setRgb(c); syncHsv(); }));
            }
        }
    }

    private static void chip(GuiGraphics g, int x, int y, int w, String label, boolean on, boolean hover) {
        if (on) { Draw.rgrad(g, x, y, w, 14, 7, Theme.accent(), Draw.shade(Theme.accent(), 0.78f)); }
        else { Draw.rgrad(g, x, y, w, 14, 7, hover ? 0xFF2A2A30 : 0xFF1C1C21, 0xFF151518); Draw.routline(g, x, y, w, 14, 7, hover ? Theme.accent(0x90) : 0x30FFFFFF); }
        Draw.centered(g, label, x + w / 2, y + 3, on ? Theme.onAccent() : 0xFFD8D8DE);
    }

    private boolean pickColor(double mx, double my, boolean start) {
        if (openColor == null || popW == 0) return false;
        int sx = popX + 8, sy = popY + 8, ss = 80, hx = sx + ss + 6;
        if (start) {
            if (Draw.inside(mx, my, sx, sy, ss, ss)) dragColor = 1;
            else if (Draw.inside(mx, my, hx - 2, sy, 14, ss)) dragColor = 2;
            else return false;
        }
        if (dragColor == 1) { hsv[1] = (float) Mth.clamp((mx - sx) / ss, 0, 1); hsv[2] = (float) Mth.clamp(1 - (my - sy) / ss, 0, 1); }
        else if (dragColor == 2) hsv[0] = (float) Mth.clamp((my - sy) / ss, 0, 0.999);
        else return false;
        applyHsv();
        return true;
    }

    /** Rounded input / dropdown field. */
    private static void field(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean active) {
        if (active) Draw.glow(g, x, y, w, h, h / 2, Theme.accent(), 2);
        Draw.rgrad(g, x, y, w, h, h / 2, hover ? 0xFF24242A : 0xFF0F0F12, hover ? 0xFF1B1B20 : 0xFF0B0B0D);
        Draw.routline(g, x, y, w, h, h / 2, active ? Theme.accent(0xD0) : hover ? Theme.accent(0x80) : 0x30FFFFFF);
    }

    /** Rounded accent button (filled + glow when hovered). */
    private static void button(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        if (hover) {
            Draw.glow(g, x, y, w, h, h / 2, Theme.accent(), 3);
            Draw.rgrad(g, x, y, w, h, h / 2, Theme.accent(), Draw.shade(Theme.accent(), 0.78f));
        } else {
            Draw.rgrad(g, x, y, w, h, h / 2, 0xFF24242A, 0xFF1A1A1F);
            Draw.routline(g, x, y, w, h, h / 2, Theme.accent(0x60));
        }
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
            if (active || hover) Draw.glow(g, cx, cy, cw, ch, 8, Theme.accent(active ? 0xFF : 0x90), 3);
            if (active) Draw.rgrad(g, cx, cy, cw, ch, 8, Theme.accent(), Draw.shade(Theme.accent(), 0.7f));
            else { Draw.rgrad(g, cx, cy, cw, ch, 8, hover ? 0xFF202026 : 0xFF17171B, 0xFF101013); Draw.routline(g, cx, cy, cw, ch, 8, hover ? Theme.accent(0x90) : 0x24FFFFFF); }
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
            Draw.rgrad(g, cx, sy, scw, sch, 8, 0xFF17171B, 0xFF101013);
            Draw.routline(g, cx, sy, scw, sch, 8, has ? Theme.accent(0x50) : 0x24FFFFFF);
            Draw.text(g, "Custom " + (i + 1), cx + 8, sy + 8, 0xFFFFFFFF);
            Draw.text(g, Presets.slotInfo(i), cx + 8, sy + 20, 0xFF7A7A7A);
            int bw = (scw - 24) / 2, by = sy + sch - 22;
            // Save
            boolean hs = Draw.inside(mx, my, cx + 8, by, bw, 16);
            button(g, cx + 8, by, bw, 16, hs);
            Draw.centered(g, "Save", cx + 8 + bw / 2, by + 4, hs ? Theme.onAccent() : 0xFFD0D0D0);
            hits.add(new Hit(cx + 8, by, bw, 16, () -> { Presets.saveSlot(slot); toast("Saved to Custom " + (slot + 1)); }));
            // Load
            int lx = cx + 16 + bw;
            boolean hl = has && Draw.inside(mx, my, lx, by, bw, 16);
            if (has) button(g, lx, by, bw, 16, hl); else { Draw.rrect(g, lx, by, bw, 16, 8, 0xFF101013); Draw.routline(g, lx, by, bw, 16, 8, 0x14FFFFFF); }
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

            // card: rounded, gradient, accent-tinted and glowing when enabled
            if (on > 0.02f || hv > 0.02f) Draw.glow(g, tx, ty, tw, TILE_H, 8, Theme.accent((int) (0xFF * Math.max(on * 0.8f, hv))), 3);
            int topC = Draw.lerp(Draw.lerp(0xFF17171B, 0xFF202026, hv), Draw.lerp(0xFF17171B, Theme.accent(0xFF), 0.16f), on);
            int botC = Draw.lerp(0xFF101013, 0xFF0F1014, on);
            Draw.rgrad(g, tx, ty, tw, TILE_H, 8, topC, botC);
            Draw.routline(g, tx, ty, tw, TILE_H, 8, on > 0.5f ? Theme.accent((int) (0x60 + 0x60 * hv)) : hv > 0.05f ? Theme.accent((int) (0x30 + 0x80 * hv)) : 0x22FFFFFF);
            g.fill(tx + 8, ty + 1, tx + tw - 8, ty + 2, 0x14FFFFFF);   // top sheen

            // icon badge: rounded square, glows when on
            int bx = tx + 8, by = ty + 8;
            if (on > 0.02f) Draw.glow(g, bx, by, 22, 22, 6, Theme.accent((int) (0xFF * on)), 2);
            Draw.rgrad(g, bx, by, 22, 22, 6, Draw.lerp(0xFF202026, Draw.shade(Theme.accent(), 0.45f), on), Draw.lerp(0xFF16161A, Draw.shade(Theme.accent(), 0.25f), on));
            Draw.routline(g, bx, by, 22, 22, 6, Theme.accent((int) (0x30 + 0xA0 * on)));
            ModuleIcons.draw(g, m.name(), bx + 3, by + 3, 16);
            if (on < 0.99f) Draw.rrect(g, bx + 1, by + 1, 20, 20, 5, ((int) (150 * (1 - on)) << 24) | 0x0B0B0B);

            int textX = bx + 28;
            Draw.text(g, m.name(), textX, by + 2, blend(0xFF9A9A9A, 0xFFFFFFFF, on));
            String d = m.description();
            int maxW = tw - (textX - tx) - 8;
            if (Draw.width(d) > maxW) d = Draw.trim(d, maxW - 6 + 6);
            Draw.text(g, d, textX, by + 13, 0xFF74747C);

            // status bar along the bottom
            int sy = ty + TILE_H - 16;
            // animated switch + state label
            int swx = tx + 8, swy = sy + 3;
            if (on > 0.02f) Draw.glow(g, swx, swy, 20, 10, 5, Theme.accent((int) (0xFF * on)), 2);
            Draw.rgrad(g, swx, swy, 20, 10, 5, Draw.lerp(0xFF2E2E34, Theme.accent(), on), Draw.lerp(0xFF222227, Draw.shade(Theme.accent(), 0.75f), on));
            Draw.rrect(g, swx + 1 + (int) (10 * on), swy + 1, 8, 8, 4, Draw.lerp(0xFF8C8C94, 0xFFFFFFFF, on));
            Draw.text(g, m.enabled() ? "ON" : "OFF", swx + 25, swy + 1, Draw.lerp(0xFF5A5A62, Theme.accent(), on));

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
        pill(g, ax, ay, 54, backHover, false);
        Draw.text(g, "‹ Back", ax + 9, ay + 5, backHover ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(ax, ay, 54, 18, () -> { settingsFor = null; pageChanged = System.currentTimeMillis(); }));

        Draw.glow(g, ax + 64, ay - 2, 22, 22, 6, Theme.accent(m.enabled() ? 0xC0 : 0x40), 2);
        Draw.rgrad(g, ax + 64, ay - 2, 22, 22, 6, Draw.shade(Theme.accent(), m.enabled() ? 0.45f : 0.15f), 0xFF15151A);
        ModuleIcons.draw(g, m.name(), ax + 66, ay, 18);
        Draw.text(g, m.name(), ax + 90, ay + 1, 0xFFFFFFFF);
        Draw.text(g, m.description(), ax + 90, ay + 11, 0xFF7A7A7A);
        int tgx = ax + aw - 30 - off;
        Draw.toggle(g, tgx, ay + 4, m.enabled());
        hits.add(new Hit(tgx - 4, ay, 30, 18, m::toggle));

        int listY = ay + 28, listH = ah - 28;
        g.fillGradient(ax, listY - 4, ax + aw - off, listY - 3, Theme.accent(0x50), 0x10FFFFFF);
        int content = m.settings().size() * SETTING_ROW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - listH));
        g.enableScissor(ax, listY, ax + aw, listY + listH);
        renderSettings(g, m, ax, listY - (int) scroll, aw - off, mx, my, listY, listH);
        g.disableScissor();
    }

    private void renderSettings(GuiGraphics g, Module m, int x, int y, int w, int mx, int my, int areaY, int areaH) {
        for (Setting<?> s : m.settings()) {
            boolean rowHover = Draw.inside(mx, my, x, y, w, SETTING_ROW - 2) && my >= areaY && my < areaY + areaH;
            Draw.rgrad(g, x, y, w, SETTING_ROW - 2, 6, rowHover ? 0xFF1E1E24 : 0xFF17171B, rowHover ? 0xFF17171C : 0xFF111114);
            if (rowHover) { Draw.routline(g, x, y, w, SETTING_ROW - 2, 6, Theme.accent(0x50)); g.fill(x + 1, y + 4, x + 3, y + SETTING_ROW - 6, Theme.accent()); }
            Draw.text(g, s.name(), x + 8, y + 6, rowHover ? 0xFFFFFFFF : 0xFFD0D0D6);
            if (!(s instanceof ActionSetting) && !(s instanceof InfoSetting) && !s.isDefault()) {   // reset to default
                int rx = x + 14 + Draw.width(s.name());
                boolean rh = Draw.inside(mx, my, rx - 2, y + 3, 12, 12);
                Draw.text(g, "\u21BA", rx, y + 6, rh ? Theme.accent() : 0xFF6A6A72);
                final Setting<?> fs = s;
                addClipped(rx - 2, y, 12, SETTING_ROW, areaY, areaH, () -> { fs.reset(); if (fs == openColor) syncHsv(); toast("Reset " + fs.name()); });
            }
            int cy = y + (SETTING_ROW - 2) / 2;
            if (s instanceof BoolSetting b) {
                int tx = x + w - 28;
                Draw.toggle(g, tx, cy - 5, b.on());
                addClipped(tx - 4, y, 30, SETTING_ROW, areaY, areaH, b::toggle);
            } else if (s instanceof ModeSetting ms) {
                int bw = 120, bx = x + w - bw - 8;
                boolean hover = Draw.inside(mx, my, bx, y + 2, bw, SETTING_ROW - 6);
                field(g, bx, y + 2, bw, SETTING_ROW - 6, hover, openMode == ms);
                Draw.text(g, openMode == ms ? "\u25B4" : "\u25BE", bx + bw - 10, y + 6, hover || openMode == ms ? Theme.accent() : 0xFF8A8A92);
                String label = ms.display();
                int lx = bx + bw / 2 - Draw.width(label) / 2;
                if (ms.isColor() && !label.equals("Global")) {
                    int idx = java.util.Arrays.asList(HudColors.NAMES).indexOf(label);
                    int sw = 0xFF000000 | HudColors.rgb(idx, 0);
                    lx += 5;
                    Draw.rrect(g, lx - 11, y + 6, 7, 7, 3, sw);
                    Draw.text(g, label, lx, y + 6, sw);
                } else {
                    Draw.text(g, label, lx, y + 6, 0xFFFFFFFF);
                }
                final int fbx = bx, fby = y + 2;
                if (ms.name().equals(pendingOpen)) { pendingOpen = null; openColor = null; openMode = ms; modeX = fbx; modeY = fby; modeW = 120; }
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, () -> {
                    if (openMode == ms) { openMode = null; return; }
                    openColor = null; openMode = ms; modeX = fbx; modeY = fby; modeW = 120; modeScroll = Math.max(0, ms.get() - 3);
                });
            } else if (s instanceof TextSetting ts) {
                int bw = Math.min(200, w / 2), bx = x + w - bw - 8;
                boolean active = editing == ts;
                field(g, bx, y + 2, bw, SETTING_ROW - 6, false, active);
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
                button(g, bx, y + 2, bw, SETTING_ROW - 6, hover);
                Draw.centered(g, as.get(), bx + bw / 2, y + 6, hover ? Theme.onAccent() : 0xFFD0D0D0);
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, as::run);
            } else if (s instanceof KeySetting ks) {
                int bw = 90, bx = x + w - bw - 8;
                boolean waiting = bindingKey == ks;
                boolean hover = Draw.inside(mx, my, bx, y + 2, bw, SETTING_ROW - 6);
                if (waiting) button(g, bx, y + 2, bw, SETTING_ROW - 6, true);
                else field(g, bx, y + 2, bw, SETTING_ROW - 6, hover, false);
                Draw.centered(g, waiting ? "Press a key..." : ks.display(), bx + bw / 2, y + 6, waiting ? Theme.onAccent() : 0xFFFFFFFF);
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, () -> bindingKey = ks);
            } else if (s instanceof InfoSetting in) {
                String v = in.text();
                int maxW = w - Draw.width(s.name()) - 28;
                if (Draw.width(v) > maxW) v = Draw.trim(v, maxW);
                Draw.text(g, v, x + w - 8 - Draw.width(v), y + 6, 0xFFB8B8B8);
            } else if (s instanceof ColorSetting cs) {
                int bw = 120, bx = x + w - bw - 8;
                boolean hover = Draw.inside(mx, my, bx, y + 2, bw, SETTING_ROW - 6);
                field(g, bx, y + 2, bw, SETTING_ROW - 6, hover, openColor == cs);
                int sw = 0xFF000000 | (cs.global() ? 0x808080 : cs.color(0));
                Draw.glow(g, bx + 5, y + 5, 10, 8, 3, sw, 2);
                Draw.rrect(g, bx + 5, y + 5, 10, 8, 3, sw);
                Draw.text(g, cs.display(), bx + 20, y + 6, cs.global() ? 0xFF9A9AA2 : 0xFFFFFFFF);
                Draw.text(g, "\u25BE", bx + bw - 10, y + 6, hover || openColor == cs ? Theme.accent() : 0xFF8A8A92);
                final int fbx = bx, fby = y + 2;
                if (cs.name().equals(pendingOpen)) { pendingOpen = null; openMode = null; openColor = cs; colX = fbx; colY = fby; syncHsv(); hexEdit.setValue(cs.hex()); }
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, () -> {
                    if (openColor == cs) { openColor = null; return; }
                    openMode = null; openColor = cs; colX = fbx; colY = fby; syncHsv();
                    hexEdit.setValue(cs.hex());
                });
            } else if (s instanceof NumberSetting n) {
                int sw = 140, sx = x + w - sw - 44;
                Draw.rrect(g, sx, cy - 2, sw, 4, 2, 0xFF2A2A30);
                int kx = sx + (int) (n.fraction() * sw);
                if (kx > sx + 1) Draw.rgrad(g, sx, cy - 2, kx - sx, 4, 2, Theme.accent(), Draw.shade(Theme.accent(), 0.75f));
                Draw.glow(g, kx - 4, cy - 4, 9, 9, 4, Theme.accent(), 2);
                Draw.rrect(g, kx - 4, cy - 4, 9, 9, 4, 0xFFFFFFFF);
                Draw.rrect(g, kx - 2, cy - 2, 5, 5, 2, Theme.accent());
                String v = n.display();
                int vw = Math.max(30, Draw.width(v) + 10), vx = x + w - 8 - vw;
                boolean vh = Draw.inside(mx, my, vx, y + 3, vw, 12);
                if (editingNum == n) {
                    field(g, vx, y + 2, vw, 14, false, true);
                    numEdit.setX(vx + 5); numEdit.setY(y + 5); numEdit.setWidth(vw - 8);
                } else {
                    Draw.rrect(g, vx, y + 3, vw, 12, 6, Theme.accent(vh ? 0x60 : 0x30));
                    Draw.centered(g, v, vx + vw / 2, y + 5, 0xFFFFFFFF);
                }
                addClipped(vx, y, vw, SETTING_ROW, areaY, areaH, () -> {   // click the number to type an exact value
                    applyNum();
                    editingNum = n; numEdit.setValue(n.display()); numEdit.moveCursorToEnd(false);
                    setFocused(numEdit); numEdit.setFocused(true);
                });
                if (y >= areaY && y + SETTING_ROW <= areaY + areaH) sliderAreas.add(new SliderArea(sx - 4, y, sw + 8, SETTING_ROW, n));
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
        button(g, bx, by, bw, 16, hover);
        Draw.centered(g, "Edit HUD", bx + bw / 2, by + 4, hover ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(bx, by, bw, 16, () -> minecraft.setScreen(new HudEditorScreen(this))));
        int cbx = bx + bw + 6, cbw = 76;
        boolean ch = Draw.inside(mx, my, cbx, by, cbw, 16);
        button(g, cbx, by, cbw, 16, ch);
        Draw.centered(g, "Cosmetics", cbx + cbw / 2, by + 4, ch ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(cbx, by, cbw, 16, () -> minecraft.setScreen(new CosmeticsScreen(parent))));
        int pbx = cbx + cbw + 6, pbw = 52;
        boolean ph = Draw.inside(mx, my, pbx, by, pbw, 16);
        button(g, pbx, by, pbw, 16, ph);
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
        if (event.button() == 0 && (openMode != null || openColor != null)) {
            double mx = event.x(), my = event.y();
            if (openColor != null && hexEdit.mouseClicked(event, doubleClick)) { setFocused(hexEdit); return true; }
            for (Hit h : List.copyOf(popupHits))
                if (Draw.inside(mx, my, h.x(), h.y(), h.w(), h.h())) { h.action().run(); return true; }
            if (pickColor(mx, my, true)) return true;
            if (Draw.inside(mx, my, popX, popY, popW, popH)) return true;
            openMode = null; openColor = null; hexEdit.setFocused(false);   // click outside closes the popup
        }
        if (event.button() == 0) {
            if (editingNum != null && numEdit.mouseClicked(event, doubleClick)) return true;
            applyNum();
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
        int key = event.key();
        if (editingNum != null && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) { applyNum(); return true; }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && (openMode != null || openColor != null || editingNum != null)) {
            openMode = null; openColor = null; editingNum = null; numEdit.setFocused(false); hexEdit.setFocused(false);
            return true;
        }
        if (bindingKey != null) {
            bindingKey.bind(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE ? -1 : event.key());
            bindingKey = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragColor != 0 && pickColor(event.x(), event.y(), false)) return true;
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
        dragColor = 0;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openMode != null) { modeScroll -= Math.signum(scrollY); return true; }
        if (openColor != null) return true;
        for (SliderArea a : sliderAreas)   // scroll over a slider to nudge it one step
            if (Draw.inside(mouseX, mouseY, a.x(), a.y(), a.w(), a.h())) { a.n().set(a.n().get() + Math.signum(scrollY) * a.n().step()); return true; }
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
