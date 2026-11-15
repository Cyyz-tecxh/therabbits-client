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
        y = row(g, mx, my, px + 6, y, pw - 12, main.getName(), AccountSwitcher.mainIsPremium() ? "Premium (launcher)" : "Launcher account",
                AccountSwitcher.usingMain(), AccountSwitcher::useMain, null);
        for (var p : AccountSwitcher.premium()) {
            y = row(g, mx, my, px + 6, y, pw - 12, p.name(), "Premium (Microsoft)", AccountSwitcher.isCurrentPremium(p),
                    () -> { status = "Signing in as " + p.name() + "..."; AccountSwitcher.usePremium(p).thenAccept(m -> status = m); }, () -> AccountSwitcher.removePremium(p));
            if (y > height - 140) break;
        }
        for (String n : AccountSwitcher.offline()) {
            y = row(g, mx, my, px + 6, y, pw - 12, n, "Cracked / offline", AccountSwitcher.isCurrent(n),
                    () -> AccountSwitcher.useOffline(n), () -> AccountSwitcher.remove(n));
            if (y > height - 100) break;
        }
        Draw.text(g, "Cracked names work on offline servers, LAN and singleplayer.", px + 8, height - 78, 0xFF6A6A6A);
        Draw.text(g, "Premium servers need a premium (launcher or Microsoft) account.", px + 8, height - 67, 0xFF6A6A6A);
        if (code != null) {
            int by = height - 136;
            Draw.rrect(g, px + 6, by, pw - 12, 50, 8, 0xFF141418);
            Draw.routline(g, px + 6, by, pw - 12, 50, 8, Theme.accent(0x90));
            Draw.text(g, "1. Open " + code.url().replace("https://", ""), px + 14, by + 6, 0xFFD0D0D6);
            Draw.text(g, "2. Enter this code and sign in:", px + 14, by + 18, 0xFFD0D0D6);
            g.pose().pushMatrix(); g.pose().translate(px + 14, by + 30); g.pose().scale(1.4f, 1.4f);
            Draw.text(g, code.userCode(), 0, 0, Theme.accent()); g.pose().popMatrix();
            button(g, mx, my, px + pw - 124, by + 28, 52, 16, "Open", () -> net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(code.url())));
            button(g, mx, my, px + pw - 68, by + 28, 54, 16, "Copy", () -> { minecraft.keyboardHandler.setClipboard(code.userCode()); status = "Code copied"; });
        }

        button(g, mx, my, px + pw - 98, height - 52, 90, 16, "Add Cracked", () -> {
            String err = AccountSwitcher.add(name.getValue());
            if (err == null) { status = "Added " + name.getValue().trim(); name.setValue(""); } else status = err;
        });
        button(g, mx, my, px + pw - 58, top + 6, 50, 16, "Done", this::onClose);
        button(g, mx, my, px + pw - 172, top + 6, 110, 16, signingIn ? "Waiting..." : "Add Premium", this::startMicrosoft);
        if (!status.isEmpty()) {
            boolean good = status.startsWith("Added") || status.startsWith("Now") || status.startsWith("Removed") || status.startsWith("Code");
            boolean info = status.startsWith("Sign") || status.startsWith("Getting");
            Draw.text(g, status, px + 8, height - 30, good ? 0xFF6AD48A : info ? 0xFFB8D8F0 : 0xFFFF7A7A);
        }
        super.render(g, mx, my, delta);
    }

    /** Microsoft device-code sign-in: shows a short code, waits for the player to finish in their browser. */
    private void startMicrosoft() {
        if (signingIn) return;
        signingIn = true;
        status = "Getting a sign-in code...";
        Thread t = new Thread(() -> {
            try {
                var c = com.therabbits.client.account.MicrosoftAuth.start();
                code = c;
                status = "Sign in at " + c.url().replace("https://", "") + " with the code below";
                while (true) {
                    Thread.sleep(c.interval() * 1000L);
                    var r = com.therabbits.client.account.MicrosoftAuth.poll(c);
                    if (r == null) continue;
                    AccountSwitcher.addPremium(r);
                    minecraft.execute(() -> AccountSwitcher.playAs(r));
                    status = "Added " + r.name() + " - now playing as " + r.name();
                    break;
                }
            } catch (com.therabbits.client.account.MicrosoftAuth.AuthException e) {
                status = e.getMessage();
            } catch (Exception e) {
                status = "Couldn't reach Microsoft - check your internet";
            } finally {
                code = null;
                signingIn = false;
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
        Draw.text(g, nm, x + 8, y + 4, 0xFFFFFFFF);
        Draw.text(g, kind, x + 8, y + 14, 0xFF6A6A6A);
        int bx = x + w - 8;
        if (remove != null) { bx -= 54; button(g, mx, my, bx, y + 5, 54, 16, "Remove", () -> { remove.run(); status = "Removed " + nm; }); bx -= 4; }
        if (current) Draw.text(g, "In use", bx - 6 - Draw.width("In use"), y + 9, 0xFF6AD48A);
        else { bx -= 40; button(g, mx, my, bx, y + 5, 40, 16, "Use", () -> { use.run(); status = "Now playing as " + nm; }); }
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
            if (name.mouseClicked(e, dbl)) return true;
            for (Hit h : List.copyOf(hits)) if (Draw.inside(e.x(), e.y(), h.x(), h.y(), h.w(), h.h())) { h.r().run(); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
