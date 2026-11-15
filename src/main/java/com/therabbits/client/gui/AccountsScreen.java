package com.therabbits.client.gui;

import com.therabbits.client.account.AccountSwitcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Switch between your launcher account, extra premium (Microsoft) accounts and offline (cracked) names. */
public class AccountsScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable r) {}

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox name;
    private String status = "";
    // Microsoft sign-in in progress
    private volatile com.therabbits.client.account.MicrosoftAuth.DeviceCode code;
    private volatile boolean signingIn;
    private volatile boolean cancelled;
    private long statusAt;

    public AccountsScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    private int pw() { return Math.min(320, width - 30); }
    private int px() { return width / 2 - pw() / 2; }

    @Override
    protected void init() {
        name = new EditBox(font, px() + 8, height - 52, pw() - 110, 16, Component.literal("Name"));
        name.setMaxLength(16);
        name.setHint(Component.literal("Cracked name, e.g. Steve"));
        name.setResponder(v -> { String pr = AccountSwitcher.nameProblem(v); name.setTextColor(pr == null ? 0xFFFFFFFF : 0xFFFF8A8A); });
        addRenderableWidget(name);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        int px = px(), pw = pw(), top = 14, bottom = height - 14;
        Draw.glow(g, px, top, pw, bottom - top, 10, Theme.accent(0x80), 4);
        Draw.rgrad(g, px, top, pw, bottom - top, 10, 0xF8141418, 0xF808080A);
        Draw.routline(g, px, top, pw, bottom - top, 10, 0x2EFFFFFF);
        Draw.text(g, "Accounts", px + 8, top + 8, 0xFFFFFFFF);
        Draw.text(g, "Playing as " + minecraft.getUser().getName(), px + 8, top + 20, 0xFF8A8A8A);

        int y = top + 38;
        var main = AccountSwitcher.main();
        y = row(g, mx, my, px + 6, y, pw - 12, main.getName(), AccountSwitcher.mainIsPremium() ? "Launcher \u00B7 Premium" : "Launcher account",
                AccountSwitcher.usingMain(), AccountSwitcher::useMain, null);
        for (var p : AccountSwitcher.premium()) {
            y = row(g, mx, my, px + 6, y, pw - 12, p.name(), "Microsoft \u00B7 Premium", AccountSwitcher.isCurrentPremium(p),
                    () -> { status = "Signing in as " + p.name() + "..."; AccountSwitcher.usePremium(p).thenAccept(m -> status = m); }, () -> AccountSwitcher.removePremium(p));
            if (y > height - 140) break;
        }
        for (String n : AccountSwitcher.offline()) {
            y = row(g, mx, my, px + 6, y, pw - 12, n, "Cracked \u00B7 Offline / LAN", AccountSwitcher.isCurrent(n),
                    () -> AccountSwitcher.useOffline(n), () -> AccountSwitcher.remove(n));
            if (y > height - 100) break;
        }
        if (AccountSwitcher.premium().isEmpty() && AccountSwitcher.offline().isEmpty() && code == null && y + 96 < height - 90) {
            // empty state: explain the two kinds of account instead of a blank panel
            int cw = (pw - 18) / 2, cy = y + 8;
            for (int k = 0; k < 2; k++) {
                int cx = px + 6 + k * (cw + 6);
                boolean hov = Draw.inside(mx, my, cx, cy, cw, 84);
                if (hov) Draw.glow(g, cx, cy, cw, 84, 8, Theme.accent(), 3);
                Draw.rgrad(g, cx, cy, cw, 84, 8, hov ? 0xFF222228 : 0xFF18181C, 0xFF0F0F12);
                Draw.routline(g, cx, cy, cw, 84, 8, hov ? Theme.accent(0xC0) : 0x28FFFFFF);
                Draw.rgrad(g, cx + 8, cy + 8, 18, 18, 6, Draw.shade(Theme.accent(), k == 0 ? 0.6f : 0.35f), 0xFF15151A);
                Draw.centered(g, k == 0 ? "M" : "C", cx + 17, cy + 13, 0xFFFFFFFF);
                Draw.text(g, k == 0 ? "Premium account" : "Cracked name", cx + 32, cy + 9, 0xFFFFFFFF);
                Draw.text(g, k == 0 ? "Microsoft sign-in" : "Offline / LAN", cx + 32, cy + 19, Theme.accent(0xC0));
                String[] lines = k == 0 ? new String[]{"Works on every server.", "Sign in at microsoft.com/link", "- no password here."}
                                        : new String[]{"Pick any name.", "For offline servers, LAN", "and singleplayer."};
                for (int j = 0; j < 3; j++) Draw.text(g, lines[j], cx + 8, cy + 36 + j * 11, 0xFF9A9AA2);
                Draw.text(g, k == 0 ? "Click to add \u203A" : "Type a name below \u2193", cx + 8, cy + 72, hov ? Theme.accent() : 0xFF6A6A72);
                if (k == 0) hits.add(new Hit(cx, cy, cw, 84, this::startMicrosoft));
                else hits.add(new Hit(cx, cy, cw, 84, () -> { setFocused(name); name.setFocused(true); }));
            }
        }
        Draw.text(g, "Cracked names work on offline servers, LAN and singleplayer.", px + 8, height - 78, 0xFF6A6A6A);
        Draw.text(g, "Premium servers need a premium (launcher or Microsoft) account.", px + 8, height - 67, 0xFF6A6A6A);
        String problem = AccountSwitcher.nameProblem(name.getValue());
        Draw.routline(g, px + 5, height - 55, pw - 112, 22, 6, problem != null ? 0xC0FF6A6A : name.isFocused() ? Theme.accent(0xC0) : 0x30FFFFFF);
        if (problem != null) Draw.text(g, problem, px + 8, height - 89, 0xFFFF8A8A);
        button(g, mx, my, px + pw - 98, height - 52, 90, 16, "Add Cracked", this::addCracked);
        button(g, mx, my, px + pw - 58, top + 6, 50, 16, "Done", this::onClose);
        button(g, mx, my, px + pw - 172, top + 6, 110, 16, signingIn ? "Signing in..." : "+ Add Premium", this::startMicrosoft);
        if (!status.isEmpty()) {
            boolean good = status.startsWith("Added") || status.startsWith("Now") || status.startsWith("Removed") || status.startsWith("Code");
            boolean info = status.startsWith("Sign") || status.startsWith("Getting");
            int sc = good ? 0xFF6AD48A : info ? 0xFFB8D8F0 : 0xFFFF7A7A;
            int sw = Draw.width(status) + 16;
            Draw.rrect(g, px + 6, height - 34, Math.min(sw, pw - 12), 16, 8, (sc & 0x00FFFFFF) | 0x26000000);
            Draw.text(g, Draw.trim(status, pw - 28), px + 14, height - 30, sc);
        }
        super.render(g, mx, my, delta);
        if (code != null || signingIn) signInPanel(g, mx, my);
    }

    private void addCracked() {
        String v = name.getValue().trim();
        String pr = AccountSwitcher.nameProblem(v);
        if (v.isEmpty()) { status = "Type a name first"; return; }
        if (pr != null) { status = pr; return; }
        String err = AccountSwitcher.add(v);
        if (err == null) { status = "Added " + v; name.setValue(""); } else status = err;
    }

    /** Modal panel while signing in with Microsoft: big code, copy/open, countdown, cancel. */
    private void signInPanel(GuiGraphics g, int mx, int my) {
        hits.clear();   // only the panel is clickable while it's open
        g.fill(0, 0, width, height, 0xB0000000);
        int w = Math.min(300, width - 30), h = 170, x = width / 2 - w / 2, y = height / 2 - h / 2;
        Draw.glow(g, x, y, w, h, 12, Theme.accent(), 5);
        Draw.rgrad(g, x, y, w, h, 12, 0xFC1A1A20, 0xFC0D0D10);
        Draw.routline(g, x, y, w, h, 12, Theme.accent(0x90));
        Draw.text(g, "Sign in with Microsoft", x + 14, y + 12, 0xFFFFFFFF);
        var c = code;
        if (c == null) {
            String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
            Draw.centered(g, "Getting your sign-in code" + dots, x + w / 2, y + 80, 0xFFB8D8F0);
        } else {
            Draw.text(g, "We opened your browser and copied the code.", x + 14, y + 26, 0xFF9A9AA2);
            Draw.text(g, "Paste it at " + c.url().replace("https://", "") + " and sign in.", x + 14, y + 37, 0xFF9A9AA2);
            // big code
            int cw = w - 28, cy = y + 54;
            Draw.rgrad(g, x + 14, cy, cw, 40, 10, 0xFF22242C, 0xFF16171C);
            Draw.routline(g, x + 14, cy, cw, 40, 10, Theme.accent(0xB0));
            g.pose().pushMatrix();
            float k = 2.2f;
            int tw = (int) (Draw.width(c.userCode()) * k);
            g.pose().translate(x + w / 2f - tw / 2f, cy + 11);
            g.pose().scale(k, k);
            Draw.text(g, c.userCode(), 0, 0, Theme.accent());
            g.pose().popMatrix();
            // countdown + waiting dots
            long left = Math.max(0, (c.expiresAt() - System.currentTimeMillis()) / 1000);
            String dots = ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
            Draw.text(g, "Waiting for you to sign in" + dots, x + 14, y + 102, 0xFFB8D8F0);
            String tl = String.format("%d:%02d", left / 60, left % 60);
            Draw.text(g, tl, x + w - 14 - Draw.width(tl), y + 102, left < 60 ? 0xFFFF8A8A : 0xFF7A7A82);
            float frac = Math.min(1f, left / 900f);
            Draw.rrect(g, x + 14, y + 114, w - 28, 3, 1, 0xFF2A2A30);
            Draw.rrect(g, x + 14, y + 114, Math.max(2, (int) ((w - 28) * frac)), 3, 1, Theme.accent());
            int bw = (w - 28 - 12) / 3, by = y + 130;
            button(g, mx, my, x + 14, by, bw, 18, "Open Browser", () -> net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(c.directUrl())));
            button(g, mx, my, x + 14 + bw + 6, by, bw, 18, "Copy Code", () -> { minecraft.keyboardHandler.setClipboard(c.userCode()); status = "Code copied"; });
            button(g, mx, my, x + 14 + 2 * (bw + 6), by, bw, 18, "Cancel", this::cancelSignIn);
            return;
        }
        button(g, mx, my, x + w / 2 - 40, y + h - 30, 80, 18, "Cancel", this::cancelSignIn);
    }

    /** Test hook: show the sign-in panel with a sample code (no network). */
    public void demoSignIn() {
        signingIn = true;
        code = new com.therabbits.client.account.MicrosoftAuth.DeviceCode("TRLH-7K2Q", "", "https://www.microsoft.com/link", 5, System.currentTimeMillis() + 812_000);
    }

    private void cancelSignIn() {
        cancelled = true;
        code = null;
        signingIn = false;
        status = "Sign-in cancelled";
    }

    /** Microsoft device-code sign-in: shows a short code, waits for the player to finish in their browser. */
    private void startMicrosoft() {
        if (signingIn) return;
        signingIn = true;
        cancelled = false;
        status = "Getting a sign-in code...";
        Thread t = new Thread(() -> {
            try {
                var c = com.therabbits.client.account.MicrosoftAuth.start();
                if (cancelled) return;
                code = c;
                status = "Sign in at " + c.url().replace("https://", "") + " with your code";
                minecraft.execute(() -> {   // save the player a step: copy the code and open the sign-in page
                    minecraft.keyboardHandler.setClipboard(c.userCode());
                    try { net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(c.directUrl())); } catch (Exception ignored) {}
                });
                while (!cancelled) {
                    Thread.sleep(c.interval() * 1000L);
                    if (cancelled) return;
                    var r = com.therabbits.client.account.MicrosoftAuth.poll(c);
                    if (r == null) continue;
                    AccountSwitcher.addPremium(r);
                    minecraft.execute(() -> AccountSwitcher.playAs(r));
                    status = "Added " + r.name() + " - now playing as " + r.name();
                    break;
                }
            } catch (com.therabbits.client.account.MicrosoftAuth.AuthException e) {
                if (!cancelled) status = e.getMessage();
            } catch (Exception e) {
                if (!cancelled) status = "Couldn't reach Microsoft - check your internet";
            } finally {
                if (!cancelled) { code = null; signingIn = false; }
            }
        }, "TheRabbits-MicrosoftLogin");
        t.setDaemon(true);
        t.start();
    }

    private int row(GuiGraphics g, int mx, int my, int x, int y, int w, String nm, String kind, boolean current, Runnable use, Runnable remove) {
        int h = 26;
        if (current) Draw.glow(g, x, y, w, h, 7, Theme.accent(), 2);
        Draw.rgrad(g, x, y, w, h, 7, current ? 0xFF20232A : 0xFF17171B, current ? 0xFF15171C : 0xFF111114);
        Draw.routline(g, x, y, w, h, 7, current ? Theme.accent(0xC0) : 0x24FFFFFF);
        // avatar: initial in a rounded tile tinted by account type, small type badge
        boolean prem = kind.contains("Premium");
        int tint = prem ? Theme.accent() : kind.startsWith("Cracked") ? 0xFFB08A5A : 0xFF8A8A92;
        Draw.rgrad(g, x + 5, y + 4, 18, 18, 5, Draw.shade(tint, 0.85f), Draw.shade(tint, 0.45f));
        Draw.centered(g, nm.isEmpty() ? "?" : nm.substring(0, 1).toUpperCase(java.util.Locale.ROOT), x + 14, y + 9, 0xFFFFFFFF);
        Draw.text(g, nm, x + 29, y + 4, 0xFFFFFFFF);
        Draw.text(g, kind, x + 29, y + 14, prem ? Theme.accent(0xC0) : 0xFF7A7A82);
        int bx = x + w - 8;
        if (remove != null) { bx -= 54; button(g, mx, my, bx, y + 5, 54, 16, "Remove", () -> { remove.run(); status = "Removed " + nm; }); bx -= 4; }
        if (current) Draw.text(g, "In use", bx - 6 - Draw.width("In use"), y + 9, 0xFF6AD48A);
        else if (AccountSwitcher.BUSY.get()) Draw.text(g, "...", bx - 14, y + 9, 0xFF8A8A92);
        else { bx -= 40; button(g, mx, my, bx, y + 5, 40, 16, "Use", () -> { use.run(); if (!status.startsWith("Signing")) status = "Now playing as " + nm; }); }
        return y + h + 4;
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, Runnable r) {
        boolean hov = Draw.inside(mx, my, x, y, w, h);
        if (hov) { Draw.glow(g, x, y, w, h, h / 2, Theme.accent(), 3); Draw.rgrad(g, x, y, w, h, h / 2, Theme.accent(), Draw.shade(Theme.accent(), 0.78f)); }
        else { Draw.rgrad(g, x, y, w, h, h / 2, 0xFF24242A, 0xFF1A1A1F); Draw.routline(g, x, y, w, h, h / 2, Theme.accent(0x60)); }
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2, hov ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(x, y, w, h, r));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            if (!(signingIn || code != null) && name.mouseClicked(e, dbl)) return true;
            for (Hit h : List.copyOf(hits)) if (Draw.inside(e.x(), e.y(), h.x(), h.y(), h.w(), h.h())) { h.r().run(); return true; }
            if (signingIn || code != null) return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if ((signingIn || code != null) && k == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { cancelSignIn(); return true; }
        if (name.isFocused() && (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || k == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) { addCracked(); return true; }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
