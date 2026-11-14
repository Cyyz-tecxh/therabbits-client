package com.therabbits.client.gui;

import com.therabbits.client.Config;
import com.therabbits.client.ModuleManager;
import com.therabbits.client.module.visual.MenuBackgroundModule;
import com.therabbits.client.mixin.PlayerSkinWidgetAccessor;
import com.therabbits.client.skin.SkinChanger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Black & white main menu.
 * Wide screens: the player's skin with lightning on the left, a glass panel (TRLH logo, title, buttons) on the right.
 * Narrow screens: just the panel, centred.
 * Clock top-left, "What's new" top-right.
 */
public class RabbitsTitleScreen extends Screen {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("therabbits", "textures/gui/glow.png");
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("therabbits", "textures/gui/vignette.png");
    private static final Identifier GRAIN = Identifier.fromNamespaceAndPath("therabbits", "textures/gui/grain.png");

    private static final int BTN_W = 210, BTN_H = 26, GAP = 5, BAR_H = 14, PANEL_PAD = 14, HEADER = 62;
    private static final String[] NEWS = {
            "New black & white menus",
            "Freelook: hold Left Alt",
            "Custom crosshair",
            "Drag your HUD with Edit HUD",
            "Hold C to zoom"
    };

    // 9x9 pixel icons, '#' = pixel
    private static final String[] ICON_PLAYER = {
            "...###...", "..#####..", "..#####..", "...###...", ".#######.", "#.#####.#", "#.#####.#", "..##.##..", "..##.##.."};
    private static final String[] ICON_GLOBE = {
            "..#####..", ".#..#..#.", "#...#...#", "#########", "#...#...#", "#########", "#...#...#", ".#..#..#.", "..#####.."};
    private static final String[] ICON_GRID = {
            "###.###..", "###.###..", "###.###..", ".........", "###.###..", "###.###..", "###.###..", ".........", "........."};
    private static final String[] ICON_GEAR = {
            "...#.#...", ".#######.", ".##...##.", "##.....##", ".#.....#.", "##.....##", ".##...##.", ".#######.", "...#.#..."};
    private static final String[] ICON_POWER = {
            "....#....", "..#.#.#..", ".#..#..#.", "#...#...#", "#.......#", "#.......#", ".#.....#.", "..#####..", "........."};

    private static final class Entry {
        final String label, hint;
        final String[] icon;
        final Runnable action;
        float hover;
        Entry(String label, String hint, String[] icon, Runnable action) {
            this.label = label; this.hint = hint; this.icon = icon; this.action = action;
        }
    }

    private static final class Bolt {
        final List<int[]> points = new ArrayList<>();
        final long born;
        Bolt(long born) { this.born = born; }
    }

    private List<Entry> entries;
    private final long opened = System.currentTimeMillis();
    private long lastFrame = opened;
    private long nextBolt = opened + 1200;
    private final List<Bolt> bolts = new ArrayList<>();
    private final Random random = new Random();
    private PlayerSkinWidget skinWidget;
    private long lastDrag;
    private String skinMessage;
    private long skinMessageTime;

    public RabbitsTitleScreen() {
        super(Component.literal("TheRabbits Client"));
    }

    @Override
    protected void init() {
        entries = List.of(
                new Entry("Singleplayer", "Your worlds", ICON_PLAYER, () -> minecraft.setScreen(new SelectWorldScreen(this))),
                new Entry("Multiplayer", "Join a server", ICON_GLOBE, () -> minecraft.setScreen(new JoinMultiplayerScreen(this))),
                new Entry("Mod Menu", "Modules & HUD", ICON_GRID, () -> minecraft.setScreen(new ModMenuScreen(this))),
                new Entry("Options", "Settings", ICON_GEAR, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))),
                new Entry("Quit", "Close game", ICON_POWER, () -> minecraft.stop())
        );
        skinWidget = null;
        if (wide()) {
            // real 3D player model (same widget as vanilla's Skin Customization screen); drag to spin
            int h = (int) (figureH() * 0.88f), w = (int) (h * 0.62f);
            skinWidget = new PlayerSkinWidget(w, h, minecraft.getEntityModels(), SkinDraw::skin);
            skinWidget.setPosition(figureCx() - w / 2, figureCy() + figureH() / 2 + 10 - h);
            addRenderableWidget(skinWidget);
        }
    }

    // ---------- layout ----------
    private boolean wide() { return width >= 460 && height >= 230; }

    /** Skin pixel size: the figure is 16 x 32 skin pixels. */
    private int px() { return Mth.clamp((height - BAR_H - 110) / 32, 2, 8); }
    private int figureW() { return 16 * px(); }
    private int figureH() { return 32 * px(); }
    private int figureCx() { return (int) (width * 0.28); }
    private int figureCy() { return (height - BAR_H) / 2 - 14; }

    private int panelW() { return BTN_W + PANEL_PAD * 2; }
    private int panelH() { return HEADER + entries.size() * (BTN_H + GAP) - GAP + PANEL_PAD; }
    private int panelX() { return wide() ? (int) (width * 0.64) - panelW() / 2 + 20 : width / 2 - panelW() / 2; }
    private int panelY() { return Math.max(40, (height - BAR_H) / 2 - panelH() / 2 + 8); }

    private int buttonsX() { return panelX() + PANEL_PAD; }
    private int buttonsY() { return panelY() + HEADER; }

    private float intro() { return Mth.clamp((System.currentTimeMillis() - opened) / 800f, 0, 1); }

    // ---------- background ----------
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        long t = now - opened;
        g.fillGradient(0, 0, width, height, 0xFF000000, 0xFF0B0B0B);

        MenuBackgroundModule bg = ModuleManager.get(MenuBackgroundModule.class);
        MenuBackgroundModule.Image img = bg.image();
        int style = bg.animated();

        if (img != null) {
            drawImage(g, img, t, bg.pan.on());
        } else if (style == 0) {
            int off = (int) (t / 60 % 24);
            for (int x = -24 + off; x < width; x += 24) g.fill(x, 0, x + 1, height, 0x08FFFFFF);
            for (int y = -24 + off; y < height; y += 24) g.fill(0, y, width, y + 1, 0x08FFFFFF);
        }

        float flash = bg.lightning.on() ? flashAmount(now) : 0;
        if (wide() && bg.glow.on()) {
            int gs = (int) (figureH() * (1.5f + 0.08f * Mth.sin(t / 900f)));
            g.blit(RenderPipelines.GUI_TEXTURED, GLOW, figureCx() - gs / 2, figureCy() - gs / 2, 0, 0, gs, gs, 256, 256, 256, 256);
        }
        // darken: lighter over images so the scene stays visible
        int dark = img != null ? 40 : 190;
        g.fill(0, 0, width, height, ((int) (dark - (dark / 2f) * flash) << 24));

        if (wide() && bg.lightning.on()) drawLightning(g, now);
        if (wide()) drawStage(g, t);

        switch (style) {
            case 0 -> specks(g, t);
            case 1 -> starfield(g, t);
            case 2 -> rain(g, t);
            case 3 -> snow(g, t);
            default -> { }
        }

        if (bg.grain.on()) {
            // re-randomise the grain every frame like real film
            int ox = -random.nextInt(128), oy = -random.nextInt(128);
            for (int gx = ox; gx < width; gx += 128)
                for (int gy = oy; gy < height; gy += 128)
                    g.blit(RenderPipelines.GUI_TEXTURED, GRAIN, gx, gy, 0, 0, 128, 128, 128, 128, 128, 128);
        }

        g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, width, height, 256, 256, 256, 256);
    }

    /** Spotlight cone + glowing ring the 3D player stands on. */
    private void drawStage(GuiGraphics g, long t) {
        int cx = figureCx();
        int top = figureCy() - figureH() / 2 - 40;
        int feet = figureCy() + figureH() / 2 + 4;
        // spotlight: widens towards the feet
        for (int y = top; y < feet; y += 2) {
            float p = (y - top) / (float) (feet - top);
            int half = (int) (figureW() * (0.25f + 0.55f * p));
            int a = (int) (26 * p);
            g.fill(cx - half, y, cx + half, y + 2, (a << 24) | 0xFFFFFF);
        }
        // ring on the floor
        float pulse = 0.75f + 0.25f * Mth.sin(t / 700f);
        int rx = (int) (figureW() * 0.75f), ry = Math.max(4, rx / 4);
        for (int i = 0; i < 180; i++) {
            double ang = i * Math.PI * 2 / 180;
            int x = cx + (int) (Math.cos(ang) * rx), y = feet + (int) (Math.sin(ang) * ry);
            int a = (int) ((ang > Math.PI ? 110 : 200) * pulse);
            g.fill(x, y, x + 2, y + 1, (a << 24) | 0xFFFFFF);
        }
        for (int k = 1; k <= 3; k++) {          // inner glow
            int r2 = rx - k * 5, ry2 = Math.max(2, r2 / 4);
            g.fill(cx - r2, feet - ry2 / 2, cx + r2, feet + ry2 / 2, ((int) (14 * pulse) << 24) | 0xFFFFFF);
        }
    }

    /** Image scaled to cover the screen, drifting slowly side to side like a camera pan. */
    private void drawImage(GuiGraphics g, MenuBackgroundModule.Image img, long t, boolean pan) {
        float scale = Math.max((float) width / img.width(), (float) height / img.height()) * (pan ? 1.10f : 1.0f);
        int dw = (int) Math.ceil(img.width() * scale), dh = (int) Math.ceil(img.height() * scale);
        float drift = pan ? Mth.sin(t / 14000f) : 0;
        int x = (width - dw) / 2 + (int) (drift * (dw - width) / 2f);
        int y = (height - dh) / 2 + (int) (Mth.cos(t / 19000f) * (pan ? (dh - height) / 3f : 0));
        g.blit(RenderPipelines.GUI_TEXTURED, img.texture(), x, y, 0, 0, dw, dh, img.width(), img.height(), img.width(), img.height());
    }

    private void specks(GuiGraphics g, long t) {
        for (int i = 0; i < 80; i++) {
            boolean near = i % 3 == 0;
            float speed = near ? 0.018f : 0.007f;
            int px = (int) ((i * 131 + t * speed * (1 + i % 5)) % Math.max(1, width));
            int py = (int) ((i * 71 + i * i * 13 + Mth.sin(t / 1500f + i) * 6) % Math.max(1, height));
            int s = near ? 2 : 1;
            g.fill(px, py, px + s, py + s, ((near ? 130 : 45) << 24) | 0xFFFFFF);
        }
    }

    /** Stars flying out from the centre. */
    private void starfield(GuiGraphics g, long t) {
        int cx = width / 2, cy = height / 2;
        float maxR = (float) Math.hypot(width, height) / 2;
        for (int i = 0; i < 160; i++) {
            float angle = (i * 2.399963f) % (float) (Math.PI * 2);
            float speed = 0.00012f + (i % 7) * 0.00003f;
            float p = ((t * speed) + (i * 0.618f)) % 1f;
            float r = p * p * maxR;
            int x = cx + (int) (Mth.cos(angle) * r), y = cy + (int) (Mth.sin(angle) * r);
            int s = p > 0.7f ? 2 : 1;
            int a = (int) (40 + 200 * p);
            g.fill(x, y, x + s, y + s, (Mth.clamp(a, 0, 255) << 24) | 0xFFFFFF);
        }
    }

    /** Thin streaks falling at an angle. */
    private void rain(GuiGraphics g, long t) {
        for (int i = 0; i < 120; i++) {
            float speed = 0.25f + (i % 5) * 0.06f;
            int len = 6 + i % 6;
            int y = (int) ((i * 97 + t * speed) % (height + 40)) - 20;
            int x = (int) ((i * 151 + y * 0.25f) % Math.max(1, width + 40)) - 20;
            int a = 40 + (i % 4) * 25;
            for (int k = 0; k < len; k++) g.fill(x + k / 4, y + k, x + k / 4 + 1, y + k + 1, (a << 24) | 0xFFFFFF);
        }
    }

    /** Slowly falling, swaying flakes. */
    private void snow(GuiGraphics g, long t) {
        for (int i = 0; i < 110; i++) {
            float speed = 0.012f + (i % 6) * 0.006f;
            int y = (int) ((i * 113 + t * speed) % (height + 10)) - 5;
            int x = (int) ((i * 173) % Math.max(1, width) + Mth.sin(t / 900f + i) * 8);
            int s = i % 4 == 0 ? 2 : 1;
            g.fill(x, y, x + s, y + s, ((i % 3 == 0 ? 200 : 110) << 24) | 0xFFFFFF);
        }
    }

    private float flashAmount(long now) {
        float f = 0;
        for (Bolt b : bolts) if (now >= b.born) f = Math.max(f, 1 - (now - b.born) / 350f);
        return Mth.clamp(f, 0, 1);
    }

    private void drawLightning(GuiGraphics g, long now) {
        if (now > nextBolt) {
            spawnBolt(now);
            if (random.nextFloat() < 0.4f) spawnBolt(now + 60);
            nextBolt = now + 3500 + random.nextInt(4000);
        }
        bolts.removeIf(b -> now - b.born > 450);
        for (Bolt b : bolts) {
            if (now < b.born) continue;
            float life = 1 - (now - b.born) / 450f;
            int a = (int) (255 * life * life);
            for (int i = 1; i < b.points.size(); i++) {
                int[] p0 = b.points.get(i - 1), p1 = b.points.get(i);
                line(g, p0[0], p0[1], p1[0], p1[1], (a / 3 << 24) | 0xFFFFFF, 3);
                line(g, p0[0], p0[1], p1[0], p1[1], (a << 24) | 0xFFFFFF, 1);
            }
        }
    }

    private void spawnBolt(long when) {
        Bolt b = new Bolt(when);
        boolean left = random.nextBoolean();
        int x = figureCx() + (left ? -1 : 1) * (figureW() / 2 + 20 + random.nextInt(40));
        int y = figureCy() - figureH() / 2 - 10;
        int endY = figureCy() + figureH() / 3;
        b.points.add(new int[]{x, y});
        while (y < endY) {
            x += random.nextInt(25) - 12 + (left ? 3 : -3);
            y += 8 + random.nextInt(12);
            b.points.add(new int[]{x, y});
        }
        bolts.add(b);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int thick) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x - thick / 2, y - thick / 2, x - thick / 2 + thick, y - thick / 2 + thick, color);
        }
    }

    // ---------- foreground ----------
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float in = intro();

        if (wide() && skinWidget != null) {
            int sy = figureCy() + figureH() / 2 + 4;
            // slow idle turn unless the player is dragging it
            PlayerSkinWidgetAccessor acc = (PlayerSkinWidgetAccessor) skinWidget;
            if (now - lastDrag > 1500) acc.therabbits$setRotationY(acc.therabbits$getRotationY() + dt * 18f);
            int bob = (int) (Mth.sin((now - opened) / 1200f) * 2);
            int lift = (int) ((1 - ease(in)) * 14);
            skinWidget.setY(figureCy() + figureH() / 2 + 10 - skinWidget.getHeight() + bob + lift);
            String name = com.therabbits.client.module.utility.AltHiderModule.displayName();
            int nw = Draw.font().width(name) + 26;
            int nx = figureCx() - nw / 2, ny = sy + 14;
            Draw.rect(g, nx, ny, nw, 16, 0xB0000000);
            Draw.rect(g, nx, ny, 2, 16, 0xFFFFFFFF);
            var tex = SkinDraw.skin().body().texturePath();
            g.blit(RenderPipelines.GUI_TEXTURED, tex, nx + 5, ny + 4, 8, 8, 8, 8, 8, 8, 64, 64);   // face
            g.blit(RenderPipelines.GUI_TEXTURED, tex, nx + 5, ny + 4, 40, 8, 8, 8, 8, 8, 64, 64);  // hat layer
            Draw.text(g, name, nx + 17, ny + 4, Theme.TEXT);
            drawSkinChanger(g, mouseX, mouseY, ny + 20);
        }

        drawPanel(g, in);

        int x = buttonsX(), y = buttonsY();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float appear = ease(Mth.clamp(in * 1.8f - 0.3f - i * 0.12f, 0, 1));
            boolean hovered = Draw.inside(mouseX, mouseY, x, y, BTN_W, BTN_H);
            e.hover = Mth.clamp(e.hover + (hovered ? dt : -dt) * 7f, 0, 1);
            drawButton(g, e, x + (int) ((1 - appear) * 16), y, appear);
            y += BTN_H + GAP;
        }

        drawClock(g);
        drawBackgroundButton(g, mouseX, mouseY);
        if (width >= 380) drawNews(g, in, now);
        drawBottomBar(g);

        if (in < 1) g.fill(0, 0, width, height, ((int) ((1 - in) * 255) << 24));
    }

    private void drawPanel(GuiGraphics g, float in) {
        int a = (int) (ease(in) * 255);
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        // TRLH logo on top of the title
        int ls = 40;
        Draw.logo(g, px + PANEL_PAD - 4, py + 6, ls);
        g.pose().pushMatrix();
        g.pose().translate(px + PANEL_PAD + ls + 2, py + 13);
        g.pose().scale(2f, 2f);
        Draw.text(g, "THE RABBITS", 0, 0, withAlpha(0xFFFFFF, a));
        g.pose().popMatrix();
        Draw.text(g, "C L I E N T", px + PANEL_PAD + ls + 3, py + 33, withAlpha(0x8A8A8A, a));
        g.fill(px + PANEL_PAD, py + HEADER - 10, px + pw - PANEL_PAD, py + HEADER - 9, withAlpha(0x3A3A3A, a));
    }

    private void drawButton(GuiGraphics g, Entry e, int x, int y, float appear) {
        int alpha = (int) (appear * 255);
        if (alpha < 8) return;
        float h = ease(e.hover);
        boolean dark = h > 0.55f;
        int ty = y + (BTN_H - 8) / 2;

        Draw.rect(g, x, y, BTN_W, BTN_H, withAlpha(0x161616, (int) (alpha * 0.85f)));
        int fill = (int) (BTN_W * h);
        if (fill > 0) Draw.rect(g, x, y, fill, BTN_H, withAlpha(0xFFFFFF, alpha));
        Draw.rect(g, x, y, 2, BTN_H, withAlpha(0xFFFFFF, alpha));

        icon(g, e.icon, x + 10 + (int) (2 * h), y + (BTN_H - 9) / 2, withAlpha(dark ? 0x000000 : 0xFFFFFF, alpha));
        Draw.text(g, e.label, x + 26 + (int) (3 * h), ty, withAlpha(dark ? 0x000000 : 0xF2F2F2, alpha));
        int hintW = Draw.font().width(e.hint);
        Draw.text(g, e.hint, x + BTN_W - hintW - 14, ty, withAlpha(dark ? 0x505050 : 0x5A5A5A, alpha));
        Draw.text(g, "›", x + BTN_W - 8 + (int) (2 * h), ty, withAlpha(dark ? 0x000000 : 0x5A5A5A, alpha));
    }

    private static void icon(GuiGraphics g, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++)
                if (rows[r].charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
    }

    // ---------- skin changer ----------
    private static final int SEL_W = 150, UP_W = 50, RS_W = 40, SC_H = 16, SC_GAP = 4;
    private int scX() { return figureCx() - (SEL_W + UP_W + RS_W + SC_GAP * 2) / 2; }
    private int scY;

    private void drawSkinChanger(GuiGraphics g, int mx, int my, int y) {
        scY = y;
        int x = scX();
        boolean hs = Draw.inside(mx, my, x, y, SEL_W, SC_H);
        Draw.rect(g, x, y, SEL_W, SC_H, hs ? 0xE0262626 : 0xC0101010);
        Draw.outline(g, x, y, SEL_W, SC_H, hs ? 0x80FFFFFF : 0x40FFFFFF);
        Draw.rect(g, x, y, 2, SC_H, 0xFFFFFFFF);
        Draw.text(g, "\u2039", x + 6, y + 4, Theme.TEXT);
        Draw.text(g, "\u203A", x + SEL_W - 9, y + 4, Theme.TEXT);
        String label = SkinChanger.currentName();
        if (Draw.font().width(label) > SEL_W - 28) label = Draw.font().plainSubstrByWidth(label, SEL_W - 34) + "..";
        Draw.centered(g, label, x + SEL_W / 2, y + 4, Theme.TEXT);

        int ux = x + SEL_W + SC_GAP;
        boolean hu = Draw.inside(mx, my, ux, y, UP_W, SC_H);
        Draw.rect(g, ux, y, UP_W, SC_H, hu ? 0xFFD0D0D0 : 0xFFFFFFFF);
        Draw.centered(g, "Upload", ux + UP_W / 2, y + 4, 0xFF000000);

        int rx = ux + UP_W + SC_GAP;
        boolean hr = Draw.inside(mx, my, rx, y, RS_W, SC_H);
        Draw.rect(g, rx, y, RS_W, SC_H, hr ? 0xE0262626 : 0xC0101010);
        Draw.outline(g, rx, y, RS_W, SC_H, hr ? 0x80FFFFFF : 0x40FFFFFF);
        Draw.centered(g, "Reset", rx + RS_W / 2, y + 4, Theme.TEXT_DIM);

        if (skinMessage != null && System.currentTimeMillis() - skinMessageTime < 4000)
            Draw.centered(g, skinMessage, figureCx(), y + SC_H + 4, 0xFFFF8080);
    }

    private boolean clickSkinChanger(double mx, double my) {
        if (!wide() || skinWidget == null) return false;
        int x = scX(), ux = x + SEL_W + SC_GAP, rx = ux + UP_W + SC_GAP;
        if (Draw.inside(mx, my, x, scY, SEL_W, SC_H)) {
            if (mx < x + SEL_W / 2.0) SkinChanger.previous(); else SkinChanger.next();
            return true;
        }
        if (Draw.inside(mx, my, ux, scY, UP_W, SC_H)) {
            skinMessage = SkinChanger.upload();
            skinMessageTime = System.currentTimeMillis();
            return true;
        }
        if (Draw.inside(mx, my, rx, scY, RS_W, SC_H)) {
            SkinChanger.reset();
            return true;
        }
        return false;
    }

    /** "What's new" card in the top-right corner. */
    private void drawNews(GuiGraphics g, float in, long now) {
        int a = (int) (ease(Mth.clamp(in * 1.5f - 0.5f, 0, 1)) * 255);
        if (a < 8) return;
        int w = 160, x = width - w - 8, y = 8;
        Draw.rect(g, x, y, w, 32, withAlpha(0x0E0E0E, (int) (a * 0.85f)));
        Draw.rect(g, x, y, 2, 32, withAlpha(0xFFFFFF, a));
        Draw.text(g, "WHAT'S NEW", x + 8, y + 5, withAlpha(0x6A6A6A, a));
        int idx = (int) ((now - opened) / 3000 % NEWS.length);
        float fade = ((now - opened) % 3000) / 3000f;
        int na = (int) (a * Math.min(1, Math.min(fade * 6, (1 - fade) * 6)));
        Draw.text(g, NEWS[idx], x + 8, y + 18, withAlpha(0xE0E0E0, na));
        for (int i = 0; i < NEWS.length; i++) {
            int dx = x + w - 6 - (NEWS.length - i) * 6;
            g.fill(dx, y + 8, dx + 4, y + 9, withAlpha(i == idx ? 0xFFFFFF : 0x3A3A3A, a));
        }
    }

    private static final int BG_X = 8, BG_Y = 36, BG_W = 120, BG_H = 14;

    /** Small button under the clock that cycles the background style. */
    private void drawBackgroundButton(GuiGraphics g, int mouseX, int mouseY) {
        MenuBackgroundModule bg = ModuleManager.get(MenuBackgroundModule.class);
        boolean hover = Draw.inside(mouseX, mouseY, BG_X, BG_Y, BG_W, BG_H);
        Draw.rect(g, BG_X, BG_Y, BG_W, BG_H, hover ? 0xFFFFFFFF : 0xC0101010);
        Draw.outline(g, BG_X, BG_Y, BG_W, BG_H, hover ? 0xFFFFFFFF : Theme.BORDER);
        Draw.text(g, "\u2039 " + bg.styleName(), BG_X + 5, BG_Y + 3, hover ? 0xFF000000 : Theme.TEXT_DIM);
        Draw.text(g, "\u203A", BG_X + BG_W - 9, BG_Y + 3, hover ? 0xFF000000 : Theme.TEXT_DIM);
    }

    /** Clock + date in the top-left corner. */
    private void drawClock(GuiGraphics g) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        g.pose().pushMatrix();
        g.pose().translate(8, 8);
        g.pose().scale(1.5f, 1.5f);
        Draw.text(g, time, 0, 0, Theme.TEXT);
        g.pose().popMatrix();
        Draw.text(g, LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM")), 8, 23, Theme.TEXT_MUTED);
    }

    private void drawBottomBar(GuiGraphics g) {
        int y = height - BAR_H;
        g.fill(0, y, width, height, 0xD0000000);
        g.fill(0, y, width, y + 1, 0x22FFFFFF);
        Draw.text(g, "TheRabbits Client  v1.1", 6, y + 3, Theme.TEXT_DIM);
        String right = "Minecraft 1.21.11";
        Draw.text(g, right, width - Draw.font().width(right) - 6, y + 3, Theme.TEXT_MUTED);
        if (width >= 380)
            Draw.centered(g, "Signed in as " + com.therabbits.client.module.utility.AltHiderModule.displayName(), width / 2, y + 3, Theme.TEXT_MUTED);
    }

    private static int withAlpha(int rgb, int a) { return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF); }

    private static float ease(float t) { return t * t * (3 - 2 * t); }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        lastDrag = System.currentTimeMillis();
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && clickSkinChanger(event.x(), event.y())) return true;
        if (Draw.inside(event.x(), event.y(), BG_X, BG_Y, BG_W, BG_H)) {
            MenuBackgroundModule bg = ModuleManager.get(MenuBackgroundModule.class);
            // left half / left click = previous, right half = next
            if (event.button() == 1 || event.x() < BG_X + BG_W / 2.0) bg.previous(); else bg.next();
            Config.save();
            return true;
        }
        if (event.button() == 0 && intro() > 0.4f) {
            int x = buttonsX(), y = buttonsY();
            for (Entry e : entries) {
                if (Draw.inside(event.x(), event.y(), x, y, BTN_W, BTN_H)) {
                    e.action.run();
                    return true;
                }
                y += BTN_H + GAP;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
