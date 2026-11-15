package com.therabbits.client.gui;

import com.therabbits.client.account.AccountSkins;
import com.therabbits.client.account.AccountSwitcher;
import com.therabbits.client.account.MicrosoftAuth;
import com.therabbits.client.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Accounts, launcher style: your accounts on the left (real skin heads), a big 3D preview of the selected
 * one on the right, and a guided Microsoft sign-in (code copied + browser opened for you, auto-retry, clear errors).
 */
public class AccountsScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable r) {}
    /** One row in the list. kind: 0 launcher, 1 premium (Microsoft), 2 cracked. */
    private record Acc(String key, String name, UUID uuid, int kind, boolean current, Runnable use, Runnable remove) {}

    private enum Panel { NONE, LOADING, CODE, SUCCESS, ERROR }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox name;
    private PlayerSkinWidget preview;
    private String status = "";
    private String selectedKey;
    private double scroll;

    // Microsoft sign-in
    private volatile Panel panel = Panel.NONE;
    private volatile MicrosoftAuth.DeviceCode code;
    private volatile String panelMsg = "";
    private volatile String welcomeName;
    private volatile UUID welcomeUuid;
    private volatile long panelAt;
    private volatile int session;   // bumps on cancel/restart so an old sign-in thread stops quietly

    public AccountsScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- layout
    private int lw() { return Math.max(220, Math.min(320, width / 2 - 20)); }
    private int lx() { return 14; }
    private int rx() { return lx() + lw() + 10; }
    private int rw() { return width - rx() - 14; }
    private int top() { return 12; }
    private int bottom() { return height - 12; }

    @Override
    protected void init() {
        name = new EditBox(font, lx() + 10, bottom() - 26, lw() - 110, 14, Component.literal("Name"));
        name.setMaxLength(16);
        name.setBordered(false);
        name.setHint(Component.literal("Cracked name, e.g. Steve"));
        name.setResponder(v -> name.setTextColor(AccountSwitcher.nameProblem(v) == null ? 0xFFFFFFFF : 0xFFFF8A8A));
        addRenderableWidget(name);
        int pw = Math.min(rw() - 40, 150), ph = Math.min(bottom() - top() - 150, 230);
        preview = new PlayerSkinWidget(pw, ph, minecraft.getEntityModels(), this::selectedSkin);
        preview.setX(rx() + rw() / 2 - pw / 2);
        preview.setY(top() + 34);
        addRenderableWidget(preview);
    }

    private List<Acc> accounts() {
        List<Acc> out = new ArrayList<>();
        var main = AccountSwitcher.main();
        out.add(new Acc("main", main.getName(), AccountSwitcher.mainIsPremium() ? main.getProfileId() : null, 0, AccountSwitcher.usingMain(),
                AccountSwitcher::useMain, null));
        for (var p : AccountSwitcher.premium())
            out.add(new Acc("premium:" + p.uuid(), p.name(), UUID.fromString(p.uuid()), 1, AccountSwitcher.isCurrentPremium(p),
                    () -> usePremium(p), () -> AccountSwitcher.removePremium(p)));
        for (String n : AccountSwitcher.offline())
            out.add(new Acc("offline:" + n, n, null, 2, AccountSwitcher.isCurrent(n), () -> AccountSwitcher.useOffline(n), () -> AccountSwitcher.remove(n)));
        return out;
    }

    private Acc selected(List<Acc> all) {
        for (Acc a : all) if (a.key().equals(selectedKey)) return a;
        for (Acc a : all) if (a.current()) return a;
        return all.get(0);
    }

    private PlayerSkin skinOf(Acc a) {
        if (a.kind() == 0 && a.current()) return SkinDraw.skin();
        return AccountSkins.get(a.name(), a.uuid());
    }

    private PlayerSkin selectedSkin() { return skinOf(selected(accounts())); }

    private static String kindLabel(int k) { return k == 0 ? "Launcher" : k == 1 ? "Microsoft" : "Cracked"; }
    private static int kindColor(int k) { return k == 0 ? 0xFF9AA0AA : k == 1 ? Theme.accent() : 0xFFD9A05A; }

    // ---------------------------------------------------------------- render
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta);
        g.fill(0, 0, width, height, 0x9A06060A);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        boolean modal = panel != Panel.NONE;
        int mxx = modal ? -9999 : mx, myy = modal ? -9999 : my;
        List<Acc> all = accounts();
        Acc sel = selected(all);

        // ---- left: list
        int lx = lx(), lw = lw(), top = top(), bot = bottom();
        panelBox(g, lx, top, lw, bot - top);
        Draw.glow(g, lx + 10, top + 9, 20, 20, 6, Theme.accent(0x80), 2);
        Draw.logo(g, lx + 10, top + 9, 20);
        Draw.text(g, "ACCOUNTS", lx + 36, top + 10, 0xFFFFFFFF);
        Draw.text(g, "Playing as " + minecraft.getUser().getName(), lx + 36, top + 21, Theme.accent(0xC0));
        button(g, mxx, myy, lx + lw - 98, top + 10, 88, 18, "+ Premium", true, this::startMicrosoft);

        int listY = top + 40, listH = bot - 46 - listY, rowH = 34;
        int content = all.size() * (rowH + 4);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, content - listH)));
        g.enableScissor(lx + 4, listY, lx + lw - 4, listY + listH);
        int y = listY - (int) scroll;
        for (Acc a : all) {
            accountRow(g, mxx, myy, a, a == sel, lx + 8, y, lw - 16, rowH, listY, listH);
            y += rowH + 4;
        }
        if (all.size() == 1) {   // only the launcher account: invite to add more
            int cy = y + 6;
            Draw.routline(g, lx + 8, cy, lw - 16, 46, 8, 0x28FFFFFF);
            Draw.centered(g, "Add a premium account with \"+ Premium\"", lx + lw / 2, cy + 11, 0xFF9A9AA2);
            Draw.centered(g, "or type a cracked name below", lx + lw / 2, cy + 25, 0xFF6A6A72);
        }
        g.disableScissor();

        // add cracked
        String problem = AccountSwitcher.nameProblem(name.getValue());
        int fy = bot - 32;
        if (problem != null) Draw.text(g, problem, lx + 10, fy - 11, 0xFFFF8A8A);
        Draw.rgrad(g, lx + 6, fy, lw - 104, 22, 11, 0xFF101013, 0xFF0B0B0D);
        Draw.routline(g, lx + 6, fy, lw - 104, 22, 11, problem != null ? 0xC0FF6A6A : name.isFocused() ? Theme.accent(0xC0) : 0x30FFFFFF);
        name.setX(lx + 14); name.setY(fy + 7); name.setWidth(lw - 120);
        button(g, mxx, myy, lx + lw - 92, fy + 2, 84, 18, "Add Cracked", false, this::addCracked);

        // ---- right: preview of the selected account
        int rx = rx(), rw = rw();
        panelBox(g, rx, top, rw, bot - top);
        for (int i = 0; i < 7; i++) Draw.rrect(g, rx + rw / 2 - 40 + i * 5, preview.getY() + preview.getHeight() - 8 + i, 80 - i * 10, 12 - i * 2, 6, Theme.accent(0x12));
        PlayerSkinWidgetAccessor acc = (PlayerSkinWidgetAccessor) preview;
        if (!preview.isHovered()) acc.therabbits$setRotationY(acc.therabbits$getRotationY() + delta * 0.6f);
        int iy = preview.getY() + preview.getHeight() + 8;
        g.pose().pushMatrix();
        g.pose().translate(rx + rw / 2f - Draw.width(sel.name()), iy);
        g.pose().scale(2f, 2f);
        Draw.text(g, sel.name(), 0, 0, 0xFFFFFFFF);
        g.pose().popMatrix();
        String sub = sel.kind() == 0 ? (AccountSwitcher.mainIsPremium() ? "Launcher account · Premium" : "Launcher account")
                : sel.kind() == 1 ? "Microsoft · Premium · works on every server" : "Cracked · offline servers, LAN, singleplayer";
        Draw.centered(g, sub, rx + rw / 2, iy + 22, kindColor(sel.kind()));
        int by = iy + 38;
        if (sel.current()) {
            int w = 120;
            Draw.rrect(g, rx + rw / 2 - w / 2, by, w, 20, 10, 0x2A6AD48A);
            Draw.centered(g, "✔ Playing now", rx + rw / 2, by + 6, 0xFF6AD48A);
        } else if (AccountSwitcher.BUSY.get()) {
            Draw.centered(g, "Signing in" + dots(), rx + rw / 2, by + 6, 0xFFB8D8F0);
        } else {
            Acc s = sel;
            button(g, mxx, myy, rx + rw / 2 - 70, by, 140, 20, "Play as " + Draw.trim(s.name(), 80), true, () -> use(s));
        }
        if (sel.remove() != null) {
            Acc s = sel;
            int rwid = Draw.width("Remove account") + 4;
            boolean h = Draw.inside(mxx, myy, rx + rw / 2 - rwid / 2, by + 26, rwid, 12);
            Draw.centered(g, "Remove account", rx + rw / 2, by + 28, h ? 0xFFFF7A7A : 0xFF6A6A72);
            hits.add(new Hit(rx + rw / 2 - rwid / 2, by + 26, rwid, 12, () -> { s.remove().run(); selectedKey = null; status = "Removed " + s.name(); }));
        }
        button(g, mxx, myy, rx + rw - 62, top + 10, 52, 18, "Done", false, this::onClose);

        if (!status.isEmpty()) {
            boolean good = status.startsWith("Added") || status.startsWith("Now") || status.startsWith("Removed") || status.startsWith("Code") || status.startsWith("Welcome");
            boolean info = status.startsWith("Sign") || status.startsWith("Getting");
            int sc = good ? 0xFF6AD48A : info ? 0xFFB8D8F0 : 0xFFFF7A7A;
            String st = Draw.trim(status, rw - 30);
            int sw = Draw.width(st) + 18;
            Draw.rrect(g, rx + rw / 2 - sw / 2, bot - 26, sw, 16, 8, (sc & 0x00FFFFFF) | 0x26000000);
            Draw.centered(g, st, rx + rw / 2, bot - 22, sc);
        }

        super.render(g, mx, my, delta);
        if (modal) signInPanel(g, mx, my);
    }

    private static String dots() { return ".".repeat((int) (System.currentTimeMillis() / 400 % 4)); }

    private void panelBox(GuiGraphics g, int x, int y, int w, int h) {
        Draw.glow(g, x, y, w, h, 12, Theme.accent(0x70), 4);
        Draw.rgrad(g, x, y, w, h, 12, 0xF4161619, 0xF4090A0C);
        Draw.routline(g, x, y, w, h, 12, 0x2EFFFFFF);
        for (int i = 0; i < w - 40; i++) {
            float f = 1f - Math.abs(i - (w - 40) / 2f) / ((w - 40) / 2f);
            g.fill(x + 20 + i, y, x + 21 + i, y + 1, Theme.accent((int) (0xD0 * f)));
        }
    }

    private void accountRow(GuiGraphics g, int mx, int my, Acc a, boolean sel, int x, int y, int w, int h, int clipY, int clipH) {
        boolean vis = y + h > clipY && y < clipY + clipH;
        boolean hov = vis && my >= clipY && my < clipY + clipH && Draw.inside(mx, my, x, y, w, h);
        if (sel || hov) Draw.glow(g, x, y, w, h, 8, Theme.accent(sel ? 0xFF : 0x80), sel ? 3 : 2);
        Draw.rgrad(g, x, y, w, h, 8, sel ? Draw.lerp(0xFF1C1C22, Theme.accent(), 0.14f) : hov ? 0xFF202026 : 0xFF17171B, 0xFF0F0F12);
        Draw.routline(g, x, y, w, h, 8, sel ? Theme.accent() : hov ? Theme.accent(0x80) : 0x22FFFFFF);
        // real skin head (face + hat layer)
        PlayerSkin skin = skinOf(a);
        int hs = h - 10;
        Draw.rrect(g, x + 4, y + 4, hs + 2, hs + 2, 4, 0xFF0A0A0C);
        g.blit(RenderPipelines.GUI_TEXTURED, skin.body().texturePath(), x + 5, y + 5, 8, 8, hs, hs, 8, 8, 64, 64);
        g.blit(RenderPipelines.GUI_TEXTURED, skin.body().texturePath(), x + 5, y + 5, 40, 8, hs, hs, 8, 8, 64, 64);
        int tx = x + hs + 12;
        Draw.text(g, Draw.trim(a.name(), w - hs - 90), tx, y + 7, 0xFFFFFFFF);
        // type badge
        String kl = kindLabel(a.kind());
        int kc = kindColor(a.kind()), kw = Draw.width(kl) + 10;
        Draw.rrect(g, tx, y + 18, kw, 11, 5, (kc & 0x00FFFFFF) | 0x30000000);
        Draw.text(g, kl, tx + 5, y + 20, kc);
        if (a.current()) {
            Draw.rrect(g, x + w - 58, y + h / 2 - 6, 50, 12, 6, 0x306AD48A);
            Draw.centered(g, "In use", x + w - 33, y + h / 2 - 4, 0xFF6AD48A);
        } else if (hov) Draw.text(g, "›", x + w - 12, y + h / 2 - 4, Theme.accent());
        if (vis) {
            int top = Math.max(y, clipY), bottom = Math.min(y + h, clipY + clipH);
            hits.add(new Hit(x, top, w, bottom - top, () -> {
                long now = System.currentTimeMillis();
                if (a.key().equals(selectedKey) && now - clickAt < 350 && !a.current()) use(a);   // double-click = play
                selectedKey = a.key(); clickAt = now;
            }));
        }
    }

    private long clickAt;

    private void use(Acc a) {
        a.use().run();
        if (a.kind() != 1) status = "Now playing as " + a.name();
    }

    private void usePremium(AccountSwitcher.Premium p) {
        status = "Signing in as " + p.name() + "...";
        AccountSwitcher.usePremium(p).thenAccept(m -> {
            status = m;
            if (m.startsWith("Sign-in expired")) {   // offer to sign that account in again
                panelMsg = "Your sign-in for " + p.name() + " expired.\nSign in again to keep using it.";
                panel = Panel.ERROR; panelAt = System.currentTimeMillis();
            }
        });
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, boolean primary, Runnable r) {
        boolean hov = Draw.inside(mx, my, x, y, w, h);
        if (hov || primary) Draw.glow(g, x, y, w, h, h / 2, Theme.accent(hov ? 0xFF : 0x70), hov ? 3 : 2);
        if (hov || primary) Draw.rgrad(g, x, y, w, h, h / 2, hov ? Theme.accent() : Draw.shade(Theme.accent(), 0.85f), Draw.shade(Theme.accent(), hov ? 0.78f : 0.6f));
        else { Draw.rgrad(g, x, y, w, h, h / 2, 0xFF24242A, 0xFF1A1A1F); Draw.routline(g, x, y, w, h, h / 2, Theme.accent(0x60)); }
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2, hov || primary ? Theme.onAccent() : 0xFFD8D8DE);
        hits.add(new Hit(x, y, w, h, r));
    }

    private void addCracked() {
        String v = name.getValue().trim();
        if (v.isEmpty()) { status = "Type a name first"; setFocused(name); name.setFocused(true); return; }
        String pr = AccountSwitcher.nameProblem(v);
        if (pr != null) { status = pr; return; }
        String err = AccountSwitcher.add(v);
        if (err == null) { status = "Added " + v; name.setValue(""); selectedKey = "offline:" + v; } else status = err;
    }

    // ---------------------------------------------------------------- Microsoft sign-in
    private void startMicrosoft() {
        int my = ++session;
        panel = Panel.LOADING; code = null; panelAt = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            int expiredRetries = 0;
            try {
                while (true) {
                    MicrosoftAuth.DeviceCode c = MicrosoftAuth.start();
                    if (my != session) return;
                    code = c; panel = Panel.CODE; panelAt = System.currentTimeMillis();
                    minecraft.execute(() -> {   // copy the code and open the page with it filled in
                        minecraft.keyboardHandler.setClipboard(c.userCode());
                        try { net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(c.directUrl())); } catch (Exception ignored) {}
                    });
                    MicrosoftAuth.Result r = null;
                    try {
                        while (r == null) {
                            Thread.sleep(c.interval() * 1000L);
                            if (my != session) return;
                            r = MicrosoftAuth.poll(c);
                        }
                    } catch (MicrosoftAuth.AuthException e) {
                        if (e.getMessage().contains("expired") && expiredRetries++ < 2) {   // code ran out: get a fresh one automatically
                            status = "Code expired - here's a new one";
                            continue;
                        }
                        throw e;
                    }
                    if (my != session) return;
                    MicrosoftAuth.Result done = r;
                    AccountSwitcher.addPremium(done);
                    minecraft.execute(() -> AccountSwitcher.playAs(done));
                    welcomeName = done.name(); welcomeUuid = done.uuid();
                    selectedKey = "premium:" + done.uuid();
                    status = "Welcome, " + done.name() + "!";
                    panel = Panel.SUCCESS; panelAt = System.currentTimeMillis();
                    Thread.sleep(1800);
                    if (my == session && panel == Panel.SUCCESS) panel = Panel.NONE;
                    return;
                }
            } catch (MicrosoftAuth.AuthException e) {
                if (my == session) { panelMsg = e.getMessage(); panel = Panel.ERROR; panelAt = System.currentTimeMillis(); }
            } catch (Exception e) {
                if (my == session) { panelMsg = "Couldn't reach Microsoft.\nCheck your internet and try again."; panel = Panel.ERROR; panelAt = System.currentTimeMillis(); }
            }
        }, "TheRabbits-MicrosoftLogin");
        t.setDaemon(true);
        t.start();
    }

    private void closePanel() {
        session++;
        if (panel == Panel.CODE || panel == Panel.LOADING) status = "Sign-in cancelled";
        panel = Panel.NONE; code = null;
    }

    private void signInPanel(GuiGraphics g, int mx, int my) {
        hits.clear();
        float in = Math.min(1f, (System.currentTimeMillis() - panelAt) / 160f);
        g.fill(0, 0, width, height, ((int) (0xB0 * in) << 24));
        int w = Math.min(320, width - 30), h = 186, x = width / 2 - w / 2, y = height / 2 - h / 2 + (int) ((1 - in) * 10);
        Draw.glow(g, x, y, w, h, 14, Theme.accent(), 6);
        Draw.rgrad(g, x, y, w, h, 14, 0xFC1B1C22, 0xFC0C0D10);
        Draw.routline(g, x, y, w, h, 14, Theme.accent(0x90));
        // header with step dots
        Draw.rgrad(g, x + 14, y + 12, 22, 22, 7, Draw.shade(Theme.accent(), 0.8f), Draw.shade(Theme.accent(), 0.4f));
        Draw.centered(g, "M", x + 25, y + 19, 0xFFFFFFFF);
        Draw.text(g, "Sign in with Microsoft", x + 44, y + 13, 0xFFFFFFFF);
        int step = switch (panel) { case LOADING -> 0; case CODE -> 1; case SUCCESS -> 2; default -> -1; };
        String[] steps = {"Get code", "Sign in", "Done"};
        for (int i = 0; i < 3; i++) {
            int sx = x + 44 + i * 58;
            boolean on = i <= step;
            Draw.rrect(g, sx, y + 26, 6, 6, 3, on ? Theme.accent() : 0xFF3A3A42);
            Draw.text(g, steps[i], sx + 9, y + 25, on ? 0xFFD8D8DE : 0xFF5A5A62);
        }
        switch (panel) {
            case LOADING -> {
                spinner(g, x + w / 2, y + 92);
                Draw.centered(g, "Getting your sign-in code" + dots(), x + w / 2, y + 122, 0xFFB8D8F0);
                button(g, mx, my, x + w / 2 - 40, y + h - 30, 80, 18, "Cancel", false, this::closePanel);
            }
            case CODE -> {
                MicrosoftAuth.DeviceCode c = code;
                if (c == null) break;
                Draw.text(g, "Your browser opened and the code is copied.", x + 16, y + 44, 0xFF9A9AA2);
                Draw.text(g, "Just sign in there - this window finishes by itself.", x + 16, y + 55, 0xFF9A9AA2);
                int cy = y + 70;
                Draw.glow(g, x + 16, cy, w - 32, 42, 10, Theme.accent(0x90), 3);
                Draw.rgrad(g, x + 16, cy, w - 32, 42, 10, 0xFF22242C, 0xFF16171C);
                Draw.routline(g, x + 16, cy, w - 32, 42, 10, Theme.accent(0xB0));
                g.pose().pushMatrix();
                float k = 2.4f;
                g.pose().translate(x + w / 2f - Draw.width(c.userCode()) * k / 2f, cy + 11);
                g.pose().scale(k, k);
                Draw.text(g, c.userCode(), 0, 0, Theme.accent());
                g.pose().popMatrix();
                long left = Math.max(0, (c.expiresAt() - System.currentTimeMillis()) / 1000);
                Draw.text(g, "Waiting for you" + dots(), x + 16, y + 120, 0xFFB8D8F0);
                String tl = String.format("%d:%02d left", left / 60, left % 60);
                Draw.text(g, tl, x + w - 16 - Draw.width(tl), y + 120, left < 60 ? 0xFFFF8A8A : 0xFF7A7A82);
                Draw.rrect(g, x + 16, y + 132, w - 32, 3, 1, 0xFF2A2A30);
                Draw.rrect(g, x + 16, y + 132, Math.max(2, (int) ((w - 32) * Math.min(1f, left / 900f))), 3, 1, Theme.accent());
                int bw = (w - 32 - 12) / 3, by = y + h - 32;
                button(g, mx, my, x + 16, by, bw, 18, "Open Browser", true, () -> net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(c.directUrl())));
                button(g, mx, my, x + 16 + bw + 6, by, bw, 18, "Copy Code", false, () -> { minecraft.keyboardHandler.setClipboard(c.userCode()); status = "Code copied"; });
                button(g, mx, my, x + 16 + 2 * (bw + 6), by, bw, 18, "Cancel", false, this::closePanel);
            }
            case SUCCESS -> {
                PlayerSkin s = AccountSkins.get(welcomeName, welcomeUuid);
                int hs = 40, hx = x + w / 2 - hs / 2, hy = y + 50;
                Draw.glow(g, hx, hy, hs, hs, 8, 0xFF6AD48A, 4);
                g.blit(RenderPipelines.GUI_TEXTURED, s.body().texturePath(), hx, hy, 8, 8, hs, hs, 8, 8, 64, 64);
                g.blit(RenderPipelines.GUI_TEXTURED, s.body().texturePath(), hx, hy, 40, 8, hs, hs, 8, 8, 64, 64);
                Draw.centered(g, "✔ Signed in", x + w / 2, y + 102, 0xFF6AD48A);
                g.pose().pushMatrix();
                g.pose().translate(x + w / 2f - Draw.width("Welcome, " + welcomeName + "!"), y + 116);
                g.pose().scale(2f, 2f);
                Draw.text(g, "Welcome, " + welcomeName + "!", 0, 0, 0xFFFFFFFF);
                g.pose().popMatrix();
                Draw.centered(g, "You're now playing on this account.", x + w / 2, y + 142, 0xFF9A9AA2);
                button(g, mx, my, x + w / 2 - 40, y + h - 30, 80, 18, "Great!", true, () -> panel = Panel.NONE);
            }
            case ERROR -> {
                Draw.rrect(g, x + w / 2 - 14, y + 50, 28, 28, 14, 0x40FF6A6A);
                Draw.centered(g, "!", x + w / 2, y + 60, 0xFFFF8A8A);
                String[] lines = panelMsg.split("\n");
                for (int i = 0; i < lines.length; i++) Draw.centered(g, Draw.trim(lines[i], w - 30), x + w / 2, y + 92 + i * 12, i == 0 ? 0xFFFFFFFF : 0xFF9A9AA2);
                button(g, mx, my, x + w / 2 - 92, y + h - 32, 88, 18, "Try again", true, this::startMicrosoft);
                button(g, mx, my, x + w / 2 + 4, y + h - 32, 88, 18, "Close", false, () -> panel = Panel.NONE);
            }
            default -> {}
        }
    }

    private static void spinner(GuiGraphics g, int cx, int cy) {
        double a0 = (System.currentTimeMillis() % 1000) / 1000.0 * Math.PI * 2;
        for (int i = 0; i < 10; i++) {
            double a = a0 + i * Math.PI * 2 / 10;
            int x = cx + (int) (Math.cos(a) * 12), y = cy + (int) (Math.sin(a) * 12);
            Draw.rrect(g, x - 2, y - 2, 4, 4, 2, Theme.accent((int) (0x30 + 0xCF * i / 9f)));
        }
    }

    /** Test hooks for screenshots (no network). */
    public void demoSignIn() {
        session++;
        code = new MicrosoftAuth.DeviceCode("TRLH-7K2Q", "", "https://www.microsoft.com/link", 5, System.currentTimeMillis() + 812_000);
        panel = Panel.CODE; panelAt = 0;
    }

    public void demoSuccess() {
        welcomeName = minecraft.getUser().getName(); welcomeUuid = UUIDUtil.createOfflinePlayerUUID(welcomeName);
        panel = Panel.SUCCESS; panelAt = 0;
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            if (panel == Panel.NONE && name.mouseClicked(e, dbl)) return true;
            for (Hit h : List.copyOf(hits)) if (Draw.inside(e.x(), e.y(), h.x(), h.y(), h.w(), h.h())) { h.r().run(); return true; }
            if (panel != Panel.NONE) return true;
        }
        if (panel != Panel.NONE) return true;
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (panel == Panel.NONE && mx < rx()) { scroll -= sy * 20; return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (panel != Panel.NONE && k == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { closePanel(); return true; }
        if (name.isFocused() && (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || k == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) { addCracked(); return true; }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() { session++; minecraft.setScreen(parent); }
}
