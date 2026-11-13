package com.therabbits.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Black & white main menu.
 * Wide screens: logo with lightning on the left, glass panel with title, buttons and news on the right.
 * Narrow screens: logo on top, buttons centred underneath.
 */
public class RabbitsTitleScreen extends Screen {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("therabbits", "textures/gui/glow.png");
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("therabbits", "textures/gui/vignette.png");

    private static final int BTN_W = 176, BTN_H = 22, GAP = 4, BAR_H = 14, PANEL_PAD = 12;
    private static final String[] NEWS = {
            "New black & white menus",
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

    /** A lightning bolt: a list of points, flashing for a short time. */
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
    }

    // ---------- layout ----------
    private boolean wide() { return width >= 440 && height >= 240; }

    private int logoSize() { return wide() ? Math.min(230, height - 60) : Math.min(110, height / 2 - 30); }
    private int logoCx() { return wide() ? (int) (width * 0.30) : width / 2; }
    private int logoCy() { return wide() ? (height - BAR_H) / 2 : 12 + logoSize() / 2; }

    private int panelW() { return BTN_W + PANEL_PAD * 2; }
    private int panelH() { return 44 + entries.size() * (BTN_H + GAP) - GAP + 14 + 48 + PANEL_PAD; }
    private int panelX() { return (int) (width * 0.62) - panelW() / 2 + 40; }
    private int panelY() { return (height - BAR_H) / 2 - panelH() / 2; }

    private int buttonsX() { return wide() ? panelX() + PANEL_PAD : width / 2 - BTN_W / 2; }
    private int buttonsY() { return wide() ? panelY() + 44 : logoCy() + logoSize() / 2 + 10; }

    private float intro() { return Mth.clamp((System.currentTimeMillis() - opened) / 800f, 0, 1); }

    // ---------- background ----------
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        long t = now - opened;
        g.fillGradient(0, 0, width, height, 0xFF000000, 0xFF0B0B0B);

        // faint grid that slowly scrolls
        int off = (int) (t / 60 % 24);
        for (int x = -24 + off; x < width; x += 24) g.fill(x, 0, x + 1, height, 0x08FFFFFF);
        for (int y = -24 + off; y < height; y += 24) g.fill(0, y, width, y + 1, 0x08FFFFFF);

        // pulsing glow behind the logo, brighter while lightning flashes
        float flash = flashAmount(now);
        int size = logoSize();
        int gs = (int) (size * (1.7f + 0.08f * Mth.sin(t / 900f)));
        g.blit(RenderPipelines.GUI_TEXTURED, GLOW, logoCx() - gs / 2, logoCy() - gs / 2, 0, 0, gs, gs, 256, 256, 256, 256);
        g.fill(0, 0, width, height, ((int) (190 - 90 * flash) << 24));

        drawLightning(g, now);

        // drifting specks in two depth layers
        for (int i = 0; i < 80; i++) {
            boolean near = i % 3 == 0;
            float speed = near ? 0.018f : 0.007f;
            int px = (int) ((i * 131 + t * speed * (1 + i % 5)) % Math.max(1, width));
            int py = (int) ((i * 71 + i * i * 13 + Mth.sin(t / 1500f + i) * 6) % Math.max(1, height));
            int s = near ? 2 : 1;
            g.fill(px, py, px + s, py + s, ((near ? 130 : 45) << 24) | 0xFFFFFF);
        }

        g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, width, height, 256, 256, 256, 256);
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
        int size = logoSize();
        boolean left = random.nextBoolean();
        int x = logoCx() + (left ? -1 : 1) * (size / 2 + random.nextInt(40));
        int y = logoCy() - size / 2 - 10;
        int endY = logoCy() + size / 3;
        b.points.add(new int[]{x, y});
        while (y < endY) {
            x += random.nextInt(25) - 12 + (left ? 3 : -3);
            y += 8 + random.nextInt(12);
            b.points.add(new int[]{x, y});
        }
        bolts.add(b);
    }

    /** Draws a line with small squares (GuiGraphics has no line primitive). */
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

        // logo: rises in, gently floats, shakes a tiny bit on lightning
        int size = logoSize();
        float flash = flashAmount(now);
        int shake = flash > 0.6f ? random.nextInt(3) - 1 : 0;
        int float_ = (int) (Mth.sin((now - opened) / 1200f) * 3);
        int ly = logoCy() - size / 2 + (int) ((1 - ease(in)) * 14) + float_;
        Draw.logo(g, logoCx() - size / 2 + shake, ly, size);

        if (wide()) drawPanel(g, in);

        int x = buttonsX(), y = buttonsY();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float appear = ease(Mth.clamp(in * 1.8f - 0.3f - i * 0.12f, 0, 1));
            boolean hovered = Draw.inside(mouseX, mouseY, x, y, BTN_W, BTN_H);
            e.hover = Mth.clamp(e.hover + (hovered ? dt : -dt) * 7f, 0, 1);
            drawButton(g, e, x + (int) ((1 - appear) * 16), y, appear);
            y += BTN_H + GAP;
        }

        if (wide()) drawNews(g, x, y + 10, in, now);
        drawTopRight(g);
        drawBottomBar(g);

        if (in < 1) g.fill(0, 0, width, height, ((int) ((1 - in) * 255) << 24));
    }

    private void drawPanel(GuiGraphics g, float in) {
        int a = (int) (ease(in) * 255);
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        Draw.rect(g, px, py, pw, ph, withAlpha(0x0A0A0A, (int) (a * 0.75f)));
        Draw.outline(g, px, py, pw, ph, withAlpha(0xFFFFFF, (int) (a * 0.12f)));
        // corner brackets
        int c = withAlpha(0xFFFFFF, a);
        g.fill(px - 1, py - 1, px + 10, py, c); g.fill(px - 1, py - 1, px, py + 10, c);
        g.fill(px + pw - 10, py + ph, px + pw + 1, py + ph + 1, c); g.fill(px + pw, py + ph - 10, px + pw + 1, py + ph + 1, c);

        // big title at 2x
        g.pose().pushMatrix();
        g.pose().translate(px + PANEL_PAD, py + 10);
        g.pose().scale(2f, 2f);
        Draw.text(g, "THE RABBITS", 0, 0, withAlpha(0xFFFFFF, a));
        g.pose().popMatrix();
        Draw.text(g, "C L I E N T", px + PANEL_PAD + 1, py + 29, withAlpha(0x8A8A8A, a));
        g.fill(px + PANEL_PAD + 70, py + 33, px + pw - PANEL_PAD, py + 34, withAlpha(0x3A3A3A, a));
    }

    private void drawButton(GuiGraphics g, Entry e, int x, int y, float appear) {
        int alpha = (int) (appear * 255);
        if (alpha < 8) return;
        float h = ease(e.hover);
        boolean dark = h > 0.55f;

        Draw.rect(g, x, y, BTN_W, BTN_H, withAlpha(0x161616, (int) (alpha * 0.85f)));
        int fill = (int) (BTN_W * h);
        if (fill > 0) Draw.rect(g, x, y, fill, BTN_H, withAlpha(0xFFFFFF, alpha));
        Draw.rect(g, x, y, 2, BTN_H, withAlpha(0xFFFFFF, alpha));

        icon(g, e.icon, x + 9 + (int) (2 * h), y + 7, withAlpha(dark ? 0x000000 : 0xFFFFFF, alpha));
        Draw.text(g, e.label, x + 24 + (int) (3 * h), y + 7, withAlpha(dark ? 0x000000 : 0xF2F2F2, alpha));
        int hintW = Draw.font().width(e.hint);
        Draw.text(g, e.hint, x + BTN_W - hintW - 14, y + 7, withAlpha(dark ? 0x505050 : 0x5A5A5A, alpha));
        Draw.text(g, "›", x + BTN_W - 8 + (int) (2 * h), y + 7, withAlpha(dark ? 0x000000 : 0x5A5A5A, alpha));
    }

    private static void icon(GuiGraphics g, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++)
                if (rows[r].charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
    }

    private void drawNews(GuiGraphics g, int x, int y, float in, long now) {
        int a = (int) (ease(Mth.clamp(in * 1.5f - 0.5f, 0, 1)) * 255);
        if (a < 8) return;
        Draw.text(g, "WHAT'S NEW", x, y, withAlpha(0x6A6A6A, a));
        g.fill(x, y + 10, x + 18, y + 11, withAlpha(0xFFFFFF, a));
        int idx = (int) ((now - opened) / 3000 % NEWS.length);
        float fade = ((now - opened) % 3000) / 3000f;
        int na = (int) (a * Math.min(1, Math.min(fade * 6, (1 - fade) * 6)));
        Draw.rect(g, x, y + 16, BTN_W, 26, withAlpha(0x161616, (int) (a * 0.85f)));
        Draw.text(g, "• " + NEWS[idx], x + 8, y + 25, withAlpha(0xD8D8D8, na));
        for (int i = 0; i < NEWS.length; i++)
            g.fill(x + BTN_W - 8 - (NEWS.length - i) * 6, y + 38, x + BTN_W - 8 - (NEWS.length - i) * 6 + 4, y + 39,
                    withAlpha(i == idx ? 0xFFFFFF : 0x3A3A3A, a));
    }

    private void drawTopRight(GuiGraphics g) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        int w = Draw.font().width(time);
        Draw.text(g, time, width - w - 8, 8, Theme.TEXT_DIM);
    }

    private void drawBottomBar(GuiGraphics g) {
        int y = height - BAR_H;
        g.fill(0, y, width, height, 0xD0000000);
        g.fill(0, y, width, y + 1, 0x22FFFFFF);
        Draw.text(g, "TheRabbits Client  v1.0", 6, y + 3, Theme.TEXT_DIM);
        String right = "Minecraft 1.21.11";
        Draw.text(g, right, width - Draw.font().width(right) - 6, y + 3, Theme.TEXT_MUTED);
        Draw.centered(g, "Signed in as " + minecraft.getUser().getName(), width / 2, y + 3, Theme.TEXT_MUTED);
    }

    private static int withAlpha(int rgb, int a) { return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF); }

    private static float ease(float t) { return t * t * (3 - 2 * t); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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
