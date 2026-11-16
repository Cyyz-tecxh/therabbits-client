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
    private static final int SIDE = 112, HEAD = 26, ROW = 18, GAP = 2, SETTING_ROW = 16, TABS_H = 13, FOOTER = 0;
    private enum Page { MODS, FAVORITES, RECENT, SETTINGS, PROFILES }
    private Page page = Page.MODS;
    private int stateFilter;                       // 0 all, 1 enabled, 2 disabled
    private Module expanded;                       // module whose settings are open inline
    private final Map<Module, Float> expandAnim = new HashMap<>();
    /** Recently toggled or configured modules (this session), newest first. */
    private static final java.util.LinkedList<String> RECENT = new java.util.LinkedList<>();
    private static void touch(Module m) { RECENT.remove(m.name()); RECENT.addFirst(m.name()); while (RECENT.size() > 10) RECENT.removeLast(); }

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
    private void closePopups() { openMode = null; openColor = null; editingNum = null; bindingKey = null; }
    public void showPresets() { closePopups(); page = Page.PROFILES; expanded = null; scroll = 0; pageChanged = System.currentTimeMillis(); }
    public void selectCategory(Category c) { closePopups(); settingsFor = null; expanded = null; chip = c == null ? 0 : switch (c) { case HUD -> 3; case COMBAT -> 4; case VISUAL -> 5; case UTILITY -> 6; default -> 0; }; page = Page.MODS; filter = c; favoritesOnly = false; scroll = 0; pageChanged = System.currentTimeMillis(); }
    /** Opens a module's settings inline (switching to its category so it is on screen). */
    public void openSettings(Module m) {
        if (m == null) return;
        if (page != Page.SETTINGS || !settingsModules().contains(m)) { page = Page.MODS; filter = m.category() == Category.COSMETICS ? null : m.category(); }
        if (page == Page.SETTINGS) { stateFilter = 0; expanded = m; expandAnim.put(m, 1f); touch(m); scroll = 0; pageChanged = System.currentTimeMillis(); return; }
        stateFilter = 0; expanded = m; expandAnim.put(m, 1f); settingsFor = m; touch(m);
        scroll = 0; scrollTo = m; pageChanged = System.currentTimeMillis();
    }
    private Module scrollTo;
    private String pendingOpen;
    /** Test hook: open the dropdown / colour picker of a setting in the open module. */
    public void openSetting(String name) { pendingOpen = name; }

    @Override
    protected void init() {
        W = Math.min(600, Math.max(380, (int) (width * 0.88f)));
        W = Math.min(W, width - 16);
        H = Math.min(340, Math.max(230, (int) (height * 0.86f)));
        H = Math.min(H, height - 16);
        searchBox = new EditBox(font, left() + 22, top() + 33, SIDE - 34, 10, Component.literal("Search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(32);
        searchBox.setTextColor(UI.TEXT);
        searchBox.setHint(Component.literal("Search"));
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
        if (minecraft.level != null) super.renderBackground(g, mouseX, mouseY, delta);   // vanilla blur: the world stays visible
        else g.fill(0, 0, width, height, 0xFF0B0E12);
        float a = ease((System.currentTimeMillis() - opened) / 220f);
        g.fill(0, 0, width, height, ((int) (90 * a) << 24) | 0x05080B);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();
        popupHits.clear();
        sliderAreas.clear();

        // open: fade in while rising 6px
        float open = ease((now - opened) / 240f);
        g.pose().pushMatrix();
        g.pose().translate(0, (1 - open) * 6);

        int x = left(), y = top();
        UI.shadow(g, x, y, W, H);
        UI.card(g, x, y, W, H, UI.BG, UI.BORDER);
        searchBox.visible = false;
        renderCards(g, x, y, mouseX, mouseY, dt);
        renderPopups(g, mouseX, mouseY);
        if (settingsFor == null && page == Page.MODS) cardTooltip(g, mouseX, mouseY); else hoverCard = null;
        hexEdit.visible = openColor != null;
        numEdit.visible = editingNum != null;
        g.pose().popMatrix();

        if (expanded == null) { editing = null; textEdit.visible = false; }
        if (toast != null && now - toastTime < 1800) {
            int tw = Draw.width(toast) + 16, tx = x + PROF + (W - PROF) / 2 - tw / 2, ty = y + H - 22;
            UI.card(g, tx, ty, tw, 14, UI.SURFACE, UI.accent(0x90));
            Draw.centered(g, toast, tx + tw / 2, ty + 3, UI.TEXT);
        }
        super.render(g, mouseX, mouseY, delta);   // edit boxes on top
    }

    // =====================================================================================
    // Card layout: tabs on top, profiles on the left, filter chips + search, a grid of mod cards.
    // Each card: big pixel icon and name, an OPTIONS bar, and a full-width ENABLED / DISABLED bar.
    // =====================================================================================
    private static final int TOP = 24, PROF = 104, CARD_H = 70, CARD_GAP = 5;
    private int chip;   // 0 all, 1 favorites, 2 recent, 3.. categories

    /** Spaced capitals, Minecraft font. */
    private static void caps(GuiGraphics g, String s, int x, int y, int color) {
        for (char c : s.toCharArray()) { Draw.text(g, String.valueOf(c), x, y, color); x += Draw.width(String.valueOf(c)) + 1; }
    }
    private static int capsWidth(String s) { int w = 0; for (char c : s.toCharArray()) w += Draw.width(String.valueOf(c)) + 1; return Math.max(0, w - 1); }

    private void renderCards(GuiGraphics g, int x, int y, int mx, int my, float dt) {
        // ---- top tabs ----
        String[] tabs = {"MODS", "SETTINGS", "COSMETICS"};
        int tw = 0; for (String t : tabs) tw += capsWidth(t) + 18 + 4;
        int tx = x + W / 2 - tw / 2;
        for (int i = 0; i < tabs.length; i++) {
            int w = capsWidth(tabs[i]) + 18;
            boolean sel = (i == 0 && page != Page.SETTINGS) || (i == 1 && page == Page.SETTINGS);
            boolean hover = Draw.inside(mx, my, tx, y + 5, w, 15);
            UI.card(g, tx, y + 5, w, 15, sel ? UI.PANEL_HI : hover ? UI.PANEL : UI.SURFACE, sel ? UI.accent(0x90) : UI.BORDER);
            caps(g, tabs[i], tx + 9, y + 9, sel ? UI.TEXT : hover ? 0xFFC8D0D8 : UI.MUTED);
            final int fi = i;
            hits.add(new Hit(tx, y + 5, w, 15, () -> {
                closePopups(); settingsFor = null; expanded = null; scroll = 0; pageChanged = System.currentTimeMillis();
                if (fi == 0) page = Page.MODS; else if (fi == 1) page = Page.SETTINGS; else minecraft.setScreen(new CosmeticsScreen(parent));
            }));
            tx += w + 4;
        }
        Draw.logo(g, x + 8, y + 5, 14);   // branding
        Draw.text(g, "TheRabbits", x + 26, y + 8, UI.TEXT);
        Draw.text(g, "1.21.11", x + 30 + Draw.width("TheRabbits"), y + 8, UI.FAINT);
        int cx = x + W - 18, cy = y + 6;   // close
        boolean ch = Draw.inside(mx, my, cx, cy, 13, 13);
        UI.card(g, cx, cy, 13, 13, ch ? UI.PANEL_HI : UI.SURFACE, UI.BORDER);
        UI.glyph(g, UI.CLOSE, cx + 4, cy + 4, ch ? UI.TEXT : UI.MUTED);
        hits.add(new Hit(cx, cy, 13, 13, this::onClose));
        g.fill(x + 1, y + TOP, x + W - 1, y + TOP + 1, UI.LINE);

        renderProfiles(g, x + 6, y + TOP + 6, PROF, H - TOP - 12, mx, my);
        int ax = x + PROF + 12, aw = W - PROF - 18, ay = y + TOP + 6, ah = H - TOP - 12;
        if (page == Page.PROFILES) renderPresets(g, ax, ay + 2, aw, ah - 2, mx, my, System.currentTimeMillis());
        else if (settingsFor != null) renderOptionsPage(g, settingsFor, ax, ay, aw, ah, mx, my);
        else if (page == Page.SETTINGS) {
            List<Module> mods = settingsModules();
            renderList(g, mods, ax, ay, aw, ah, mx, my, dt);
        } else {
            renderChips(g, ax, ay, aw, mx, my);
            renderGridCards(g, cardMods(), ax, ay + 20, aw, ah - 20, mx, my, dt);
        }
    }

    private void renderProfiles(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        UI.box(g, x, y, w, h, 0x40000000);
        UI.label(g, "Profiles", x + 5, y + 5);
        int ry = y + 16;
        for (int i = 0; i < Presets.BUILT_IN.size(); i++) {
            Presets.BuiltIn p = Presets.BUILT_IN.get(i);
            boolean act = Presets.active(p), hv = Draw.inside(mx, my, x + 2, ry, w - 4, 14);
            UI.box(g, x + 2, ry, w - 4, 14, act ? UI.PANEL_HI : hv ? UI.PANEL : UI.SURFACE);
            if (act) g.fill(x + 2, ry + 2, x + 4, ry + 12, UI.accent());
            Draw.text(g, Draw.trim(p.name(), w - 16), x + 8, ry + 3, act ? UI.TEXT : UI.MUTED);
            hits.add(new Hit(x + 2, ry, w - 4, 14, () -> { Presets.apply(p); toast("Loaded " + p.name()); }));
            ry += 15;
        }
        for (int i = 0; i < Presets.SLOTS; i++) {
            if (!Presets.hasSlot(i)) continue;
            final int slot = i;
            boolean hv = Draw.inside(mx, my, x + 2, ry, w - 4, 14);
            UI.box(g, x + 2, ry, w - 4, 14, hv ? UI.PANEL : UI.SURFACE);
            PixelIcons.draw(g, "_profiles", x + 6, ry + 3, 1, hv ? UI.accent() : UI.FAINT);
            Draw.text(g, "Profile " + (i + 1), x + 18, ry + 3, hv ? UI.TEXT : UI.MUTED);
            UI.glyph(g, UI.CHEV_RIGHT, x + w - 12, ry + 4, hv ? UI.TEXT : UI.FAINT);
            hits.add(new Hit(x + 2, ry, w - 4, 14, () -> { Presets.loadSlot(slot); toast("Loaded Profile " + (slot + 1)); }));
            ry += 15;
        }
        // stats
        long on = ModuleManager.all().stream().filter(mm -> mm.category() != Category.COSMETICS && mm.enabled()).count();
        long all = ModuleManager.all().stream().filter(mm -> mm.category() != Category.COSMETICS).count();
        int stY = y + h - 58;
        g.fill(x + 4, stY - 4, x + w - 4, stY - 3, UI.LINE);
        Draw.text(g, on + " / " + all + " mods on", x + 6, stY + 2, UI.FAINT);
        int bw2 = w - 12;
        g.fill(x + 6, stY + 13, x + 6 + bw2, stY + 15, 0xFF2C3540);
        g.fill(x + 6, stY + 13, x + 6 + (int) (bw2 * on / (double) Math.max(1, all)), stY + 15, UI.accent());
        // bottom: save as new profile, edit HUD layout
        int by = y + h - 36;
        boolean h1 = Draw.inside(mx, my, x + 3, by, w - 6, 14);
        UI.card(g, x + 3, by, w - 6, 14, h1 ? UI.PANEL : UI.SURFACE, UI.BORDER);
        String sl = "SAVE AS NEW PROFILE";
        if (capsWidth(sl) > w - 12) sl = "SAVE PROFILE";
        caps(g, sl, x + w / 2 - capsWidth(sl) / 2, by + 3, h1 ? UI.TEXT : UI.MUTED);
        hits.add(new Hit(x + 3, by, w - 6, 14, () -> {
            for (int i = 0; i < Presets.SLOTS; i++) if (!Presets.hasSlot(i)) { Presets.saveSlot(i); toast("Saved as Profile " + (i + 1)); return; }
            toast("All " + Presets.SLOTS + " profile slots are used");
        }));
        boolean h2 = Draw.inside(mx, my, x + 3, by + 18, w - 6, 15);
        UI.box(g, x + 3, by + 18, w - 6, 15, h2 ? Draw.shade(UI.accent(), 1.08f) : UI.accent());
        String el = capsWidth("EDIT HUD LAYOUT") > w - 12 ? "EDIT HUD" : "EDIT HUD LAYOUT";
        caps(g, el, x + w / 2 - capsWidth(el) / 2, by + 22, UI.ICE_INK);
        hits.add(new Hit(x + 3, by + 18, w - 6, 15, () -> minecraft.setScreen(new HudEditorScreen(this))));
    }

    private static final String[] CHIPS = {"ALL", "FAVORITES", "RECENT", "HUD", "PVP", "VISUAL", "UTILITY"};

    private void renderChips(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int cx = x;
        for (int i = 0; i < CHIPS.length; i++) {
            int cw = capsWidth(CHIPS[i]) + 10;
            boolean sel = chip == i, hv = Draw.inside(mx, my, cx, y, cw, 14);
            UI.box(g, cx, y, cw, 14, sel ? UI.accent() : hv ? UI.PANEL_HI : UI.PANEL);
            caps(g, CHIPS[i], cx + 5, y + 3, sel ? UI.ICE_INK : hv ? UI.TEXT : UI.MUTED);
            final int fi = i;
            hits.add(new Hit(cx, y, cw, 14, () -> { chip = fi; scroll = 0; pageChanged = System.currentTimeMillis(); }));
            cx += cw + 3;
        }
        // search on the right
        int sw = Math.max(60, x + w - cx - 4), sx = x + w - sw;
        boolean focus = searchBox.isFocused();
        UI.field(g, sx, y, sw, 14, Draw.inside(mx, my, sx, y, sw, 14), focus);
        UI.glyph(g, UI.SEARCH, sx + 4, y + 4, focus ? UI.accent() : UI.FAINT);
        searchBox.setX(sx + 13); searchBox.setY(y + 3); searchBox.setWidth(sw - 16);
        searchBox.visible = true;
        hits.add(new Hit(sx, y, sw, 14, () -> { setFocused(searchBox); searchBox.setFocused(true); }));
    }

    private List<Module> cardMods() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        List<Module> base = ModuleManager.all();
        if (chip == 2) {
            base = new ArrayList<>();
            for (String n : RECENT) for (Module m : ModuleManager.all()) if (m.name().equals(n)) base.add(m);
        }
        for (Module m : base) {
            if (m.category() == Category.COSMETICS) continue;
            if (!q.isEmpty()) { if (matches(m, q)) out.add(m); continue; }
            if (chip == 1 && !Favorites.is(m.name())) continue;
            if (chip >= 3 && m.category() != new Category[]{Category.HUD, Category.COMBAT, Category.VISUAL, Category.UTILITY}[chip - 3]) continue;
            out.add(m);
        }
        return out;
    }

    private void renderGridCards(GuiGraphics g, List<Module> mods, int ax, int ay, int aw, int ah, int mx, int my, float dt) {
        int cols = Math.max(2, Math.min(5, (aw + CARD_GAP) / (96 + CARD_GAP)));
        int cw = (aw - (cols - 1) * CARD_GAP - 4) / cols;
        int rows = (mods.size() + cols - 1) / cols;
        int content = rows * (CARD_H + CARD_GAP) - CARD_GAP;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - ah));
        float slide = ease((System.currentTimeMillis() - pageChanged) / 180f);
        g.enableScissor(ax, ay, ax + aw, ay + ah);
        if (mods.isEmpty()) Draw.centered(g, chip == 1 ? "No favorites yet. Star a mod in its options." : chip == 2 ? "Mods you change will show up here." : "Nothing matches \"" + search + "\"", ax + aw / 2, ay + 30, UI.FAINT);
        boolean inArea = my >= ay && my < ay + ah;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int cx = ax + (i % cols) * (cw + CARD_GAP);
            int cy = ay + (i / cols) * (CARD_H + CARD_GAP) - (int) scroll + Math.round((1 - slide) * 6);
            if (cy + CARD_H < ay || cy > ay + ah) continue;
            drawCard(g, m, cx, cy, cw, mx, my, inArea, dt);
        }
        // soft fades where the list is cut off
        if (scroll > 0.5) g.fillGradient(ax, ay, ax + aw, ay + 10, 0xC0101419, 0x00101419);
        if (scroll < content - ah - 0.5) g.fillGradient(ax, ay + ah - 12, ax + aw, ay + ah, 0x00101419, 0xD0101419);
        g.disableScissor();
        if (content > ah) {
            int bh = Math.max(16, ah * ah / content), by = ay + (int) ((ah - bh) * (scroll / (content - ah)));
            g.fill(ax + aw - 2, by, ax + aw, by + bh, 0x40FFFFFF);
        }
    }

    private Module hoverCard, lastHoverCard;
    private long hoverCardSince;

    /** Description tooltip after hovering a card for a moment. */
    private void cardTooltip(GuiGraphics g, int mx, int my) {
        Module m = hoverCard;
        lastHoverCard = m;
        hoverCard = null;
        if (m == null || openMode != null || openColor != null || System.currentTimeMillis() - hoverCardSince < 450) return;
        String line2 = m.settings().isEmpty() ? "Click to turn on or off" : "Click to toggle  \u00B7  Right-click for options";
        int w = Math.max(Draw.width(m.description()), Draw.width(line2)) + 12, h = 26;
        int tx = Math.min(width - w - 4, mx + 10), ty = Math.min(height - h - 4, my + 12);
        UI.shadow(g, tx, ty, w, h);
        UI.card(g, tx, ty, w, h, UI.SURFACE, UI.BORDER);
        Draw.text(g, m.description(), tx + 6, ty + 4, UI.TEXT);
        Draw.text(g, line2, tx + 6, ty + 15, UI.FAINT);
    }

    private void drawCard(GuiGraphics g, Module m, int x, int y, int w, int mx, int my, boolean inArea, float dt) {
        boolean hover = inArea && Draw.inside(mx, my, x, y, w, CARD_H);
        float hv = approach(hoverAnim, m, hover ? 1 : 0, dt * 12);
        float on = approach(toggleAnim, m, m.enabled() ? 1 : 0, dt * 12);
        int top = CARD_H - 29;
        y -= Math.round(hv);   // lifts 1px on hover
        UI.card(g, x, y, w, CARD_H, Draw.lerp(UI.PANEL, UI.PANEL_HI, hv), hover ? 0xFF46525F : UI.BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x12FFFFFF);   // top bevel, like a Minecraft button
        if (hover) { hoverCard = m; hoverCardSince = hoverCard == lastHoverCard ? hoverCardSince : System.currentTimeMillis(); }
        // icon (3x pixel glyph) and name
        int icol = Draw.lerp(0xFF8A96A3, 0xFFFFFFFF, Math.max(on, hv * 0.5f));
        if (ModuleIcons.hasCustom(m.name())) ModuleIcons.draw(g, m.name(), x + w / 2 - 9, y + 7, 18);
        else PixelIcons.draw(g, m.name(), x + w / 2 - 9, y + 7, 2, icol);
        boolean fav = Favorites.is(m.name());
        boolean fh = inArea && Draw.inside(mx, my, x + w - 12, y + 2, 10, 10);
        if (fav || hover) UI.glyph(g, fav ? UI.STAR : UI.STAR_EMPTY, x + w - 9, y + 4, fav ? UI.WARN : fh ? UI.TEXT : UI.FAINT);
        if (inArea) hits.add(new Hit(x + w - 12, y + 2, 10, 10, () -> { Favorites.toggle(m.name()); toast(Favorites.is(m.name()) ? "Added to favorites" : "Removed from favorites"); }));
        String name = Draw.trim(m.name(), w - 8);
        Draw.centered(g, name, x + w / 2, y + top - 11, Draw.lerp(UI.MUTED, UI.TEXT, Math.max(on, hv)));
        // OPTIONS bar
        int oy = y + top;
        boolean oh = inArea && Draw.inside(mx, my, x + 1, oy, w - 2, 14);
        g.fill(x + 1, oy, x + w - 1, oy + 14, oh ? 0xFF2E3946 : 0xFF1A2129);
        g.fill(x + 1, oy, x + w - 1, oy + 1, UI.LINE);
        boolean has = !m.settings().isEmpty();
        caps(g, "OPTIONS", x + (w - 14) / 2 - capsWidth("OPTIONS") / 2, oy + 4, has ? (oh ? UI.TEXT : 0xFFC8D0D8) : UI.FAINT);
        g.fill(x + w - 15, oy, x + w - 14, oy + 14, UI.LINE);
        PixelIcons.draw(g, "_gear", x + w - 12, oy + 3, 1, oh ? UI.accent() : UI.MUTED);
        if (inArea) hits.add(new Hit(x + 1, oy, w - 2, 14, () -> openOptions(m)));
        // ENABLED / DISABLED bar
        int sy = oy + 14;
        boolean sh = inArea && Draw.inside(mx, my, x + 1, sy, w - 2, 15);
        int onC = 0xFF3F9A5C, offC = 0xFF9E3A43;
        int bar = Draw.lerp(offC, onC, on);
        if (sh) bar = Draw.shade(bar, 1.12f);
        g.fill(x + 1, sy, x + w - 1, sy + 15, bar);
        g.fill(x + 1, sy + 14, x + w - 1, sy + 15, Draw.shade(bar, 0.7f));   // pixel bevel
        g.fill(x + 1, sy, x + w - 1, sy + 1, Draw.shade(bar, 1.25f));
        String st = m.enabled() ? "ENABLED" : "DISABLED";
        caps(g, st, x + w / 2 - capsWidth(st) / 2, sy + 4, 0xFFFFFFFF);
        if (inArea) {
            hits.add(new Hit(x + 1, sy, w - 2, 15, () -> { m.toggle(); touch(m); }));
            hits.add(new Hit(x, y, w, top, () -> { m.toggle(); touch(m); }, has ? () -> openOptions(m) : null));
        }
    }

    private void openOptions(Module m) {
        if (m.settings().isEmpty()) { toast(m.name() + " has no options"); return; }
        closePopups(); settingsFor = m; expanded = m; touch(m); scroll = 0; pageChanged = System.currentTimeMillis();
    }

    /** Full-width options page for one mod: back, icon, name, favourite, switch, then its settings. */
    private void renderOptionsPage(GuiGraphics g, Module m, int ax, int ay, int aw, int ah, int mx, int my) {
        float slide = ease((System.currentTimeMillis() - pageChanged) / 180f);
        ax += Math.round((1 - slide) * 10);
        boolean bh = Draw.inside(mx, my, ax, ay, 40, 14);
        UI.card(g, ax, ay, 40, 14, bh ? UI.PANEL_HI : UI.PANEL, UI.BORDER);
        caps(g, "BACK", ax + 20 - capsWidth("BACK") / 2, ay + 3, bh ? UI.TEXT : UI.MUTED);
        hits.add(new Hit(ax, ay, 40, 14, () -> { closePopups(); settingsFor = null; expanded = null; pageChanged = System.currentTimeMillis(); }));
        PixelIcons.draw(g, m.name(), ax + 48, ay + 2, 1, m.enabled() ? UI.accent() : UI.MUTED);
        Draw.text(g, m.name(), ax + 62, ay + 3, UI.TEXT);
        Draw.text(g, m.description(), ax + 68 + Draw.width(m.name()), ay + 3, UI.FAINT);
        int sx = ax + aw - 20;
        UI.toggle(g, sx, ay + 3, m.enabled() ? 1 : 0);
        hits.add(new Hit(sx - 3, ay, 22, 14, () -> { m.toggle(); touch(m); }));
        boolean fav = Favorites.is(m.name());
        boolean fh = Draw.inside(mx, my, sx - 14, ay + 2, 9, 10);
        UI.glyph(g, fav ? UI.STAR : UI.STAR_EMPTY, sx - 12, ay + 4, fav ? UI.WARN : fh ? UI.TEXT : UI.FAINT);
        hits.add(new Hit(sx - 14, ay, 10, 14, () -> Favorites.toggle(m.name())));
        int listY = ay + 20, listH = ah - 20;
        UI.box(g, ax, listY, aw, listH, 0x30000000);
        int content = m.settings().size() * SETTING_ROW + 8;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - listH));
        g.enableScissor(ax, listY, ax + aw, listY + listH);
        renderSettings(g, m, ax + 8, listY + 4 - (int) scroll, aw - 16, mx, my, listY, listH);
        g.disableScissor();
    }

    // ---------- sidebar ----------
    private int navY;
    private void nav(GuiGraphics g, int x, int w, String label, String icon, String count, boolean sel, int mx, int my, Runnable action) {
        int y = navY, h = 14;
        boolean hover = Draw.inside(mx, my, x, y, w, h);
        if (sel) { UI.box(g, x, y, w, h, UI.PANEL); g.fill(x, y + 2, x + 2, y + h - 2, UI.accent()); }
        else if (hover) UI.box(g, x, y, w, h, 0x0CFFFFFF);
        PixelIcons.draw(g, icon, x + 6, y + 3, 1, sel ? UI.accent() : hover ? 0xFFC8D0D8 : UI.FAINT);
        Draw.text(g, label, x + 20, y + 3, sel ? UI.TEXT : hover ? 0xFFDDE3E9 : UI.MUTED);
        if (count != null) Draw.text(g, count, x + w - 5 - Draw.width(count), y + 3, UI.FAINT);
        hits.add(new Hit(x, y, w, h, action));
        navY += h + 1;
    }

    private void renderSidebar(GuiGraphics g, int x, int y, int mx, int my) {
        g.fill(x + 1, y + 1, x + SIDE, y + H - 1, UI.SURFACE);
        g.fill(x + SIDE - 1, y + 1, x + SIDE, y + H - 1, UI.LINE);
        int ix = x + 8, iw = SIDE - 16;
        Draw.logo(g, ix, y + 6, 14);
        Draw.text(g, "TheRabbits", ix + 19, y + 7, UI.TEXT);
        Draw.text(g, "1.21.11", ix + 19, y + 16, UI.FAINT);

        // search
        boolean focus = searchBox.isFocused();
        if (page != Page.PROFILES) UI.field(g, ix, y + 27, iw, 14, Draw.inside(mx, my, ix, y + 27, iw, 14), focus);
        if (page != Page.PROFILES) UI.glyph(g, UI.SEARCH, ix + 5, y + 31, focus ? UI.accent() : UI.FAINT);
        searchBox.setX(ix + 14); searchBox.setY(y + 30); searchBox.setWidth(iw - 18);
        searchBox.visible = page != Page.PROFILES;
        hits.add(new Hit(ix, y + 27, iw, 14, () -> { setFocused(searchBox); searchBox.setFocused(true); }));

        navY = y + 48;
        UI.label(g, "Library", ix + 2, navY); navY += 10;
        int all = (int) ModuleManager.all().stream().filter(m -> m.category() != Category.COSMETICS).count();
        nav(g, ix, iw, "All mods", "_grid", String.valueOf(all), page == Page.MODS && filter == null, mx, my, () -> { selectCategory(null); });
        nav(g, ix, iw, "Favorites", "_star", null, page == Page.FAVORITES, mx, my, () -> { closePopups(); page = Page.FAVORITES; scroll = 0; pageChanged = System.currentTimeMillis(); });
        nav(g, ix, iw, "Recently used", "_recent", null, page == Page.RECENT, mx, my, () -> { closePopups(); page = Page.RECENT; scroll = 0; pageChanged = System.currentTimeMillis(); });
        navY += 5;
        UI.label(g, "Categories", ix + 2, navY); navY += 10;
        for (Category c : Category.values()) {
            if (c == Category.COSMETICS) continue;
            int n = ModuleManager.in(c).size();
            nav(g, ix, iw, c.label(), catIcon(c), String.valueOf(n), page == Page.MODS && filter == c, mx, my, () -> selectCategory(c));
        }
        nav(g, ix, iw, "Cosmetics", "_shirt", null, false, mx, my, () -> minecraft.setScreen(new CosmeticsScreen(parent)));

        // bottom block: profiles, settings, edit HUD / packs
        navY = y + H - 62;
        g.fill(ix, navY - 5, ix + iw, navY - 4, UI.LINE);
        nav(g, ix, iw, "Profiles", "_profiles", null, page == Page.PROFILES, mx, my, this::showPresets);
        nav(g, ix, iw, "Settings", "_gear", null, page == Page.SETTINGS, mx, my, () -> { closePopups(); page = Page.SETTINGS; scroll = 0; pageChanged = System.currentTimeMillis(); });
        int by = y + H - 22, bw = (iw - 3) / 2;
        boolean h1 = Draw.inside(mx, my, ix, by, bw, 15), h2 = Draw.inside(mx, my, ix + bw + 3, by, bw, 15);
        UI.button(g, ix, by, bw, 15, "Edit HUD", h1, false);
        UI.button(g, ix + bw + 3, by, bw, 15, "Packs", h2, false);
        hits.add(new Hit(ix, by, bw, 15, () -> minecraft.setScreen(new HudEditorScreen(this))));
        hits.add(new Hit(ix + bw + 3, by, bw, 15, () -> minecraft.setScreen(new PackCustomizerScreen(this))));
    }

    // ---------- main header ----------
    private void renderMainHeader(GuiGraphics g, int x, int y, int w, String title, String sub, int mx, int my, boolean chips) {
        Draw.text(g, title, x + 10, y + 9, UI.TEXT);
        Draw.text(g, sub, x + 16 + Draw.width(title), y + 9, UI.FAINT);
        // close
        int cx = x + w - 14, cy = y + 9;
        boolean ch = Draw.inside(mx, my, cx - 3, cy - 3, 11, 11);
        UI.glyph(g, UI.CLOSE, cx, cy, ch ? UI.TEXT : UI.FAINT);
        hits.add(new Hit(cx - 3, cy - 3, 11, 11, this::onClose));
        if (chips) {
            String[] names = {"All", "Enabled", "Disabled"};
            int tx = cx - 10;
            for (int i = names.length - 1; i >= 0; i--) {
                int cw = Draw.width(names[i]) + 12; tx -= cw + 2;
                final int fi = i;
                UI.chip(g, tx, y + 6, cw, TABS_H, names[i], stateFilter == i, Draw.inside(mx, my, tx, y + 6, cw, TABS_H));
                hits.add(new Hit(tx, y + 6, cw, TABS_H, () -> { stateFilter = fi; scroll = 0; }));
            }
        }
        g.fill(x, y + HEAD - 1, x + w - 1, y + HEAD, UI.LINE);
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
        UI.shadow(g, x, y, w, h);
        UI.card(g, x, y, w, h, UI.SURFACE, UI.BORDER);
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
                if (sel) { UI.box(g, x + 3, iy, modeW - 6, ih, UI.PANEL); g.fill(x + 3, iy + 3, x + 4, iy + ih - 3, UI.accent()); }
                else if (hov) UI.box(g, x + 3, iy, modeW - 6, ih, 0x10FFFFFF);
                String lbl = ms.label(i);
                int tx = x + 9;
                if (ms.isColor() && !lbl.equals("Global")) {
                    int idx = java.util.Arrays.asList(HudColors.NAMES).indexOf(lbl);
                    if (idx >= 0) { UI.box(g, tx, iy + 3, 7, 7, 0xFF000000 | HudColors.rgb(idx, 0)); tx += 11; }
                }
                Draw.text(g, Draw.trim(lbl, modeW - (tx - x) - 6), tx, iy + 3, sel ? UI.TEXT : UI.MUTED);
                final int fi = i;
                popupHits.add(new Hit(x + 3, iy, modeW - 6, ih, () -> { ms.set(fi); openMode = null; }));
            }
            if (n > vis) {   // scroll hint
                int bh = Math.max(10, h * vis / n), by = y + (int) ((h - bh) * (modeScroll / (n - vis)));
                g.fill(x + modeW - 3, by, x + modeW - 2, by + bh, UI.FAINT);
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
            UI.border(g, sx - 1, sy - 1, ss + 2, ss + 2, UI.BORDER);
            int kx = sx + (int) (hsv[1] * ss), ky = sy + (int) ((1 - hsv[2]) * ss);
            UI.border(g, kx - 3, ky - 3, 7, 7, 0xFFFFFFFF);
            // hue bar
            int hx = sx + ss + 6, hw = 10;
            for (int j = 0; j < ss; j++) g.fill(hx, sy + j, hx + hw, sy + j + 1, 0xFF000000 | java.awt.Color.HSBtoRGB(j / (float) ss, 1f, 1f));
            int hy = sy + (int) (hsv[0] * ss);
            g.fill(hx - 2, hy - 1, hx + hw + 2, hy + 1, 0xFFFFFFFF);
            // right column: preview + hex + options
            int rx = hx + hw + 8, rw = x + w - 8 - rx;
            int cur = 0xFF000000 | cs.color(0);
            UI.card(g, rx, sy, rw, 14, cur, UI.BORDER);
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
                UI.box(g, px, py, pw, pw, 0xFF000000 | c);
                if (sel || Draw.inside(mx, my, px, py, pw, pw)) UI.border(g, px - 1, py - 1, pw + 2, pw + 2, sel ? UI.accent() : 0xFFFFFFFF);
                popupHits.add(new Hit(px, py, pw, pw, () -> { cs.setRgb(c); syncHsv(); }));
            }
        }
    }

    private static void chip(GuiGraphics g, int x, int y, int w, String label, boolean on, boolean hover) {
        if (on) { UI.card(g, x, y, w, 14, UI.PANEL, UI.accent(0xC0)); Draw.centered(g, label, x + w / 2, y + 3, UI.TEXT); }
        else { UI.card(g, x, y, w, 14, hover ? UI.PANEL_HI : UI.PANEL, UI.BORDER); Draw.centered(g, label, x + w / 2, y + 3, hover ? UI.TEXT : UI.MUTED); }
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

    private static void field(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean active) { UI.field(g, x, y, w, h, hover, active); }

    /** Flat button; the label is drawn by the caller. */
    private static void button(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        UI.card(g, x, y, w, h, hover ? UI.PANEL_HI : UI.PANEL, hover ? UI.accent(0xB0) : UI.BORDER);
    }

    private static final String[] ICON_STAR = {
            "....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..", ".##...##.", "#.......#"};

    // ---------- presets page ----------
    private void renderPresets(GuiGraphics g, int ax, int ay, int aw, int ah, int mx, int my, long now) {
        float slide = ease((now - pageChanged) / 200f);
        ay += (int) ((1 - slide) * 12);
        UI.label(g, "Built-in", ax, ay);
        int cols = 4, cw = (aw - (cols - 1) * GAP) / cols, ch = 62, cy = ay + 12;
        for (int i = 0; i < Presets.BUILT_IN.size(); i++) {
            Presets.BuiltIn p = Presets.BUILT_IN.get(i);
            int cx = ax + i * (cw + GAP);
            boolean active = Presets.active(p);
            boolean hover = Draw.inside(mx, my, cx, cy, cw, ch);
            UI.card(g, cx, cy, cw, ch, hover ? UI.PANEL_HI : UI.PANEL, active ? UI.accent() : UI.BORDER);
            if (active) g.fill(cx + 1, cy + 1, cx + 3, cy + ch - 1, UI.accent());
            int fg = UI.TEXT, sub = UI.MUTED;
            Draw.text(g, p.name(), cx + 8, cy + 8, fg);
            String d = p.description();
            if (Draw.width(d) > cw - 14) d = Draw.trim(d, cw - 20 + 6);
            Draw.text(g, d, cx + 8, cy + 20, sub);
            Draw.text(g, Presets.count(p) + " features", cx + 8, cy + ch - 14, sub);
            Draw.text(g, active ? "Active" : "Load", cx + cw - 8 - Draw.width(active ? "Active" : "Load"), cy + ch - 14, active ? UI.accent() : UI.MUTED);
            hits.add(new Hit(cx, cy, cw, ch, () -> { Presets.apply(p); toast("Loaded " + p.name()); }));
        }

        int sy = cy + ch + 16;
        UI.label(g, "Your profiles", ax, sy);
        int scw = (aw - (Presets.SLOTS - 1) * GAP) / Presets.SLOTS, sch = 58;
        sy += 12;
        for (int i = 0; i < Presets.SLOTS; i++) {
            final int slot = i;
            int cx = ax + i * (scw + GAP);
            boolean has = Presets.hasSlot(i);
            UI.card(g, cx, sy, scw, sch, UI.PANEL, UI.BORDER);
            Draw.text(g, "Profile " + (i + 1), cx + 8, sy + 8, UI.TEXT);
            Draw.text(g, Draw.trim(Presets.slotInfo(i), scw - 16), cx + 8, sy + 20, UI.MUTED);
            int bw = (scw - 24) / 2, by = sy + sch - 22;
            // Save
            boolean hs = Draw.inside(mx, my, cx + 8, by, bw, 16);
            button(g, cx + 8, by, bw, 16, hs);
            Draw.centered(g, "Save", cx + 8 + bw / 2, by + 5, hs ? UI.TEXT : 0xFFD0D6DC);
            hits.add(new Hit(cx + 8, by, bw, 16, () -> { Presets.saveSlot(slot); toast("Saved to Profile " + (slot + 1)); }));
            // Load
            int lx = cx + 16 + bw;
            boolean hl = has && Draw.inside(mx, my, lx, by, bw, 16);
            if (has) button(g, lx, by, bw, 16, hl); else UI.card(g, lx, by, bw, 16, UI.SURFACE, UI.LINE);
            Draw.centered(g, "Load", lx + bw / 2, by + 5, !has ? UI.FAINT : hl ? UI.TEXT : 0xFFD0D6DC);
            if (has) hits.add(new Hit(lx, by, bw, 16, () -> { Presets.loadSlot(slot); toast("Loaded Profile " + (slot + 1)); }));
        }
    }

    private void toast(String text) { toast = text; toastTime = System.currentTimeMillis(); }

    private static String catIcon(Category c) {
        return switch (c) { case HUD -> "_hud"; case COMBAT -> "_sword"; case VISUAL -> "_eye"; case UTILITY -> "_wrench"; case COSMETICS -> "_shirt"; };
    }

    private static String[] icon(Category c) {
        return switch (c) {
            case HUD -> ICON_HUD;
            case COMBAT -> ICON_PVP;
            case VISUAL -> ICON_VISUAL;
            case UTILITY -> ICON_UTILITY;
            case COSMETICS -> ICON_STAR;
        };
    }

    private static final String[] ICON_CLOCK = {"..###..", ".#...#.", "#..#..#", "#..##.#", "#.....#", ".#...#.", "..###.."};
    private static final String[] ICON_SHIRT = {".##.##.", "#######", "#.###.#", "..###..", "..###..", "..###..", "..###.."};
    private static final String[] ICON_LAYERS = {"...#...", ".##.##.", "#.....#", ".##.##.", "#.###.#", ".#...#.", "...#..."};

    // ---------- module list ----------
    private List<Module> settingsModules() {
        List<Module> out = new ArrayList<>();
        for (Class<? extends Module> c : List.of(com.therabbits.client.module.utility.ClientSettingsModule.class,
                com.therabbits.client.module.hud.HudStyleModule.class, com.therabbits.client.module.visual.ClientFontModule.class)) {
            Module m = ModuleManager.get(c);
            if (m != null) out.add(m);
        }
        return out;
    }

    private List<Module> visibleModules() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Module> base;
        if (!q.isEmpty()) base = ModuleManager.all();
        else if (page == Page.SETTINGS) base = settingsModules();
        else if (page == Page.RECENT) {
            base = new ArrayList<>();
            for (String n : RECENT) for (Module m : ModuleManager.all()) if (m.name().equals(n)) base.add(m);
        } else base = ModuleManager.all();
        List<Module> out = new ArrayList<>();
        for (Module m : base) {
            if (m.category() == Category.COSMETICS) continue;   // cosmetics live in their own screen
            if (q.isEmpty() && page == Page.MODS && filter != null && m.category() != filter) continue;
            if (q.isEmpty() && page == Page.FAVORITES && !Favorites.is(m.name())) continue;
            if (stateFilter == 1 && !m.enabled() || stateFilter == 2 && m.enabled()) continue;
            if (!q.isEmpty() && !matches(m, q)) continue;
            out.add(m);
        }
        return out;
    }

    /** Search looks at the name, the description and every setting name. */
    private static boolean matches(Module m, String q) {
        if (m.name().toLowerCase(Locale.ROOT).contains(q) || m.description().toLowerCase(Locale.ROOT).contains(q)) return true;
        for (Setting<?> s : m.settings()) if (s.name().toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    private int settingsHeight(Module m) { return m.settings().size() * SETTING_ROW + 7; }

    /** One placed item in the list: a section header (m == null) or a module card. */
    private record Slot(Module m, String header, int x, int y, int w, int h, int extra) {}

    private void renderList(GuiGraphics g, List<Module> mods, int ax, int ay, int aw, int ah, int mx, int my, float dt) {
        float slide = ease((System.currentTimeMillis() - pageChanged) / 180f);
        if (page == Page.MODS && filter == null && search.isBlank()) { mods = new ArrayList<>(mods); mods.sort(java.util.Comparator.comparingInt(m -> m.category().ordinal())); }
        boolean twoCols = aw >= 380;
        boolean grouped = page == Page.MODS && filter == null && search.isBlank();
        int colW = twoCols ? (aw - GAP) / 2 : aw;

        // layout: cards flow in two columns; an open card takes the full width; "All mods" is grouped by category
        List<Slot> slots = new ArrayList<>();
        int y = 0, col = 0;
        Category lastCat = null;
        for (Module m : mods) {
            if (grouped && m.category() != lastCat) {
                if (col != 0) { y += ROW + GAP; col = 0; }
                slots.add(new Slot(null, m.category().label(), 0, y + (lastCat == null ? 0 : 3), aw, 11, 0));
                y += lastCat == null ? 12 : 15;
                lastCat = m.category();
            }
            float e = approach(expandAnim, m, m == expanded ? 1 : 0, dt * 7);
            int extra = Math.round(e * settingsHeight(m));
            if (m == scrollTo) { scroll = Math.max(0, y - 14); scrollTo = null; }
            if (e > 0.001f || !twoCols) {
                if (col != 0) { y += ROW + GAP; col = 0; }
                slots.add(new Slot(m, null, 0, y, aw, ROW, extra));
                y += ROW + extra + GAP;
            } else {
                slots.add(new Slot(m, null, col * (colW + GAP), y, colW, ROW, 0));
                if (++col == 2) { col = 0; y += ROW + GAP; }
            }
        }
        if (col != 0) y += ROW + GAP;
        int content = Math.max(0, y - GAP);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - ah));
        if (expanded != null && !mods.contains(expanded)) { expanded = null; settingsFor = null; }

        g.enableScissor(ax, ay, ax + aw + 4, ay + ah);
        if (mods.isEmpty()) {
            String msg = page == Page.FAVORITES ? "No favorites yet. Click the star on any mod."
                    : page == Page.RECENT ? "Mods you change will show up here."
                    : "Nothing matches \"" + search + "\"";
            Draw.centered(g, msg, ax + aw / 2, ay + 30, UI.FAINT);
        }
        int oy = ay - (int) scroll + Math.round((1 - slide) * 8);
        boolean inArea = my >= ay && my < ay + ah;
        for (Slot s : slots) {
            int sy = oy + s.y();
            if (sy + s.h() + s.extra() < ay || sy > ay + ah) continue;
            if (s.m() == null) {
                UI.label(g, s.header(), ax + s.x() + 1, sy + 2);
                int lw = Draw.width(s.header().toUpperCase(Locale.ROOT)) + 6;
                g.fill(ax + s.x() + lw, sy + 5, ax + s.x() + s.w(), sy + 6, UI.LINE);
            } else drawRow(g, s.m(), ax + s.x(), sy, s.w(), s.extra(), mx, my, inArea, ay, ah, dt);
        }
        g.disableScissor();
        if (content > ah) {
            int bh = Math.max(16, ah * ah / content), by = ay + (int) ((ah - bh) * (scroll / (content - ah)));
            g.fill(ax + aw + 2, by, ax + aw + 4, by + bh, 0x40FFFFFF);
        }
    }

    private void drawRow(GuiGraphics g, Module m, int x, int y, int w, int extra, int mx, int my, boolean inArea, int areaY, int areaH, float dt) {
        boolean hover = inArea && Draw.inside(mx, my, x, y, w, ROW);
        float hv = approach(hoverAnim, m, hover ? 1 : 0, dt * 12);
        float on = approach(toggleAnim, m, m.enabled() ? 1 : 0, dt * 12);
        boolean open = m == expanded;

        // compact row: flat panel, a lighter shade on hover, an ice bar on the left when open
        int bg = open ? UI.PANEL : Draw.lerp(0xFF1A2129, UI.PANEL, hv);
        UI.box(g, x, y, w, ROW + extra, bg);
        if (open) { UI.border(g, x, y, w, ROW + extra, UI.BORDER); g.fill(x, y + 1, x + 2, y + ROW - 1, UI.accent()); }

        // icon: pixel glyph (or the player's own custom icon), ice when on
        int ic = Draw.lerp(UI.FAINT, UI.accent(), on);
        if (ModuleIcons.hasCustom(m.name())) ModuleIcons.draw(g, m.name(), x + 6, y + (ROW - 10) / 2, 10);
        else PixelIcons.draw(g, m.name(), x + 7, y + (ROW - 9) / 2, 1, ic);

        int tx = x + 22, ty = y + (ROW - 8) / 2;
        Draw.text(g, m.name(), tx, ty, Draw.lerp(0xFFAEB8C2, UI.TEXT, Math.max(on, hv * 0.6f)));
        int right = x + w - 6;
        int sx = right - 16, sy = y + (ROW - 8) / 2;
        UI.toggle(g, sx, sy, on);
        if (inArea) hits.add(new Hit(sx - 3, y, 22, ROW, () -> { m.toggle(); touch(m); }));
        int ax = sx - 11;
        if (!m.settings().isEmpty()) {   // configure arrow
            boolean ch = inArea && Draw.inside(mx, my, ax - 3, y, 10, ROW);
            if (open) UI.glyph(g, UI.CHEV_DOWN, ax, y + ROW / 2 - 1, UI.accent());
            else UI.glyph(g, UI.CHEV_RIGHT, ax + 1, y + ROW / 2 - 2, ch ? UI.TEXT : hover ? UI.MUTED : UI.FAINT);
            if (inArea) hits.add(new Hit(ax - 3, y, 10, ROW, () -> toggleExpand(m)));
            ax -= 11;
        }
        {   // favourite star: always shown when set, otherwise only on hover
            boolean fav = Favorites.is(m.name());
            boolean fh = inArea && Draw.inside(mx, my, ax - 2, y, 9, ROW);
            if (fav || hover) UI.glyph(g, fav ? UI.STAR : UI.STAR_EMPTY, ax, y + ROW / 2 - 2, fav ? UI.WARN : fh ? UI.TEXT : UI.FAINT);
            if (inArea) hits.add(new Hit(ax - 2, y, 9, ROW, () -> { Favorites.toggle(m.name()); toast(Favorites.is(m.name()) ? "Added to favorites" : "Removed from favorites"); }));
        }
        // subtle description after the name when there is room
        int dx = tx + Draw.width(m.name()) + 8, dw = ax - 6 - dx;
        if (dw > 30) Draw.text(g, Draw.trim(m.description(), dw), dx, ty, Draw.lerp(0xFF4F5A66, UI.FAINT, Math.max(hv, open ? 1 : 0)));
        // click the row: open settings (or toggle a mod that has none); right-click also opens settings
        if (inArea) hits.add(new Hit(x, y, w, ROW, m.settings().isEmpty() ? () -> { m.toggle(); touch(m); } : () -> toggleExpand(m),
                m.settings().isEmpty() ? null : () -> toggleExpand(m)));

        if (extra > 2) {
            int top = Math.max(areaY, y + ROW), bottom = Math.min(areaY + areaH, y + ROW + extra);
            if (bottom > top) {
                g.fill(x + 1, y + ROW, x + w - 1, y + ROW + 1, UI.LINE);
                g.enableScissor(x, top, x + w, bottom);
                renderSettings(g, m, x + 22, y + ROW + 4, w - 28, mx, my, top, bottom - top);
                g.disableScissor();
            }
        }
    }

    private void toggleExpand(Module m) {
        openMode = null; openColor = null; editingNum = null;
        if (expanded == m) { expanded = null; settingsFor = null; return; }
        expanded = m; settingsFor = m; touch(m);
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

    // ---------- inline settings ----------
    private void renderSettings(GuiGraphics g, Module m, int x, int y, int w, int mx, int my, int areaY, int areaH) {
        for (Setting<?> s : m.settings()) {
            boolean rowHover = Draw.inside(mx, my, x, y, w, SETTING_ROW - 2) && my >= areaY && my < areaY + areaH;
            if (rowHover) UI.box(g, x - 4, y, w + 4, SETTING_ROW - 2, 0x0AFFFFFF);
            Draw.text(g, s.name(), x, y + 4, rowHover ? UI.TEXT : UI.MUTED);
            if (!(s instanceof ActionSetting) && !(s instanceof InfoSetting) && !s.isDefault()) {   // reset to default
                int rx = x + 6 + Draw.width(s.name());
                boolean rh = Draw.inside(mx, my, rx - 2, y + 3, 12, 12);
                Draw.text(g, "\u21BA", rx, y + 4, rh ? UI.accent() : UI.FAINT);
                final Setting<?> fs = s;
                addClipped(rx - 2, y, 12, SETTING_ROW, areaY, areaH, () -> { fs.reset(); if (fs == openColor) syncHsv(); toast("Reset " + fs.name()); });
            }
            int cy = y + (SETTING_ROW - 2) / 2;
            if (s instanceof BoolSetting b) {
                int tx = x + w - 20;
                UI.toggle(g, tx, cy - 4, b.on() ? 1 : 0);
                addClipped(tx - 4, y, 30, SETTING_ROW, areaY, areaH, b::toggle);
            } else if (s instanceof ModeSetting ms) {
                int bw = 120, bx = x + w - bw - 8;
                boolean hover = Draw.inside(mx, my, bx, y + 1, bw, SETTING_ROW - 2);
                field(g, bx, y + 1, bw, SETTING_ROW - 2, hover, openMode == ms);
                UI.glyph(g, UI.CHEV_DOWN, bx + bw - 10, y + 6, hover || openMode == ms ? UI.accent() : UI.FAINT);
                String label = ms.display();
                int lx = bx + bw / 2 - Draw.width(label) / 2;
                if (ms.isColor() && !label.equals("Global")) {
                    int idx = java.util.Arrays.asList(HudColors.NAMES).indexOf(label);
                    int sw = 0xFF000000 | HudColors.rgb(idx, 0);
                    lx += 5;
                    UI.box(g, lx - 11, y + 4, 7, 7, sw);
                    Draw.text(g, label, lx, y + 4, UI.TEXT);
                } else {
                    Draw.text(g, label, lx, y + 4, UI.TEXT);
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
                field(g, bx, y + 1, bw, SETTING_ROW - 2, false, active);
                if (active) {
                    textEdit.setX(bx + 4); textEdit.setY(y + 5); textEdit.setWidth(bw - 8);
                    textEdit.visible = y >= areaY && y + SETTING_ROW <= areaY + areaH;
                } else {
                    String v = ts.get();
                    if (Draw.width(v) > bw - 10) v = Draw.trim(v, bw - 16 + 6);
                    Draw.text(g, v.isEmpty() ? "Click to type" : v, bx + 5, y + 4, v.isEmpty() ? UI.FAINT : UI.TEXT);
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
                boolean hover = Draw.inside(mx, my, bx, y + 1, bw, SETTING_ROW - 2);
                button(g, bx, y + 1, bw, SETTING_ROW - 2, hover);
                Draw.centered(g, as.get(), bx + bw / 2, y + 4, hover ? UI.TEXT : 0xFFD0D6DC);
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, as::run);
            } else if (s instanceof KeySetting ks) {
                int bw = 90, bx = x + w - bw - 8;
                boolean waiting = bindingKey == ks;
                boolean hover = Draw.inside(mx, my, bx, y + 1, bw, SETTING_ROW - 2);
                field(g, bx, y + 1, bw, SETTING_ROW - 2, hover, waiting);
                Draw.centered(g, waiting ? "Press a key..." : ks.display(), bx + bw / 2, y + 4, waiting ? UI.accent() : UI.TEXT);
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, () -> bindingKey = ks);
            } else if (s instanceof InfoSetting in) {
                String v = in.text();
                int maxW = w - Draw.width(s.name()) - 28;
                if (Draw.width(v) > maxW) v = Draw.trim(v, maxW);
                Draw.text(g, v, x + w - 8 - Draw.width(v), y + 4, UI.MUTED);
            } else if (s instanceof ColorSetting cs) {
                int bw = 120, bx = x + w - bw - 8;
                boolean hover = Draw.inside(mx, my, bx, y + 1, bw, SETTING_ROW - 2);
                field(g, bx, y + 1, bw, SETTING_ROW - 2, hover, openColor == cs);
                int sw = 0xFF000000 | (cs.global() ? 0x808080 : cs.color(0));
                UI.card(g, bx + 4, y + 4, 10, 8, sw, 0x40000000);
                Draw.text(g, cs.display(), bx + 19, y + 4, cs.global() ? UI.MUTED : UI.TEXT);
                UI.glyph(g, UI.CHEV_DOWN, bx + bw - 10, y + 6, hover || openColor == cs ? UI.accent() : UI.FAINT);
                final int fbx = bx, fby = y + 2;
                if (cs.name().equals(pendingOpen)) { pendingOpen = null; openMode = null; openColor = cs; colX = fbx; colY = fby; syncHsv(); hexEdit.setValue(cs.hex()); }
                addClipped(bx, y, bw, SETTING_ROW, areaY, areaH, () -> {
                    if (openColor == cs) { openColor = null; return; }
                    openMode = null; openColor = cs; colX = fbx; colY = fby; syncHsv();
                    hexEdit.setValue(cs.hex());
                });
            } else if (s instanceof NumberSetting n) {
                int sw = 120, sx = x + w - sw - 46;
                UI.slider(g, sx, cy, sw, (float) n.fraction(), rowHover);
                String v = n.display();
                int vw = Math.max(30, Draw.width(v) + 10), vx = x + w - 8 - vw;
                boolean vh = Draw.inside(mx, my, vx, y + 3, vw, 12);
                if (editingNum == n) {
                    field(g, vx, y + 1, vw, 14, false, true);
                    numEdit.setX(vx + 5); numEdit.setY(y + 5); numEdit.setWidth(vw - 8);
                } else {
                    UI.card(g, vx, y + 1, vw, 14, vh ? UI.PANEL_HI : UI.SURFACE, vh ? UI.accent(0x90) : UI.LINE);
                    Draw.centered(g, v, vx + vw / 2, y + 4, UI.TEXT);
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
            if (searchBox.visible && searchBox.mouseClicked(event, doubleClick)) { setFocused(searchBox); return true; }
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
