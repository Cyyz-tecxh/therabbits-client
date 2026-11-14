package com.therabbits.client.gui;

import com.therabbits.client.account.AccountSwitcher;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Switch between your launcher account and offline (cracked) names. */
public class AccountsScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable r) {}

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox name;
    private String status = "";

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
        Draw.rect(g, px, top, pw, bottom - top, 0xF00B0B0B);
        Draw.outline(g, px, top, pw, bottom - top, 0x30FFFFFF);
        Draw.text(g, "Accounts", px + 8, top + 8, 0xFFFFFFFF);
        Draw.text(g, "Playing as " + minecraft.getUser().getName(), px + 8, top + 20, 0xFF8A8A8A);

        int y = top + 38;
        var main = AccountSwitcher.main();
        y = row(g, mx, my, px + 6, y, pw - 12, main.getName(), AccountSwitcher.mainIsPremium() ? "Premium (launcher)" : "Launcher account",
                AccountSwitcher.usingMain(), AccountSwitcher::useMain, null);
        for (String n : AccountSwitcher.offline()) {
            y = row(g, mx, my, px + 6, y, pw - 12, n, "Cracked / offline", AccountSwitcher.isCurrent(n),
                    () -> AccountSwitcher.useOffline(n), () -> AccountSwitcher.remove(n));
            if (y > height - 100) break;
        }
        Draw.text(g, "Cracked names work on offline servers, LAN and singleplayer.", px + 8, height - 78, 0xFF6A6A6A);
        Draw.text(g, "Premium servers need your launcher account.", px + 8, height - 67, 0xFF6A6A6A);

        button(g, mx, my, px + pw - 98, height - 52, 90, 16, "Add Cracked", () -> {
            String err = AccountSwitcher.add(name.getValue());
            if (err == null) { status = "Added " + name.getValue().trim(); name.setValue(""); } else status = err;
        });
        button(g, mx, my, px + pw - 58, top + 6, 50, 16, "Done", this::onClose);
        if (!status.isEmpty()) Draw.text(g, status, px + 8, height - 30, (status.startsWith("Added") || status.startsWith("Now") || status.startsWith("Removed")) ? 0xFF6AD48A : 0xFFFF7A7A);
        super.render(g, mx, my, delta);
    }

    private int row(GuiGraphics g, int mx, int my, int x, int y, int w, String nm, String kind, boolean current, Runnable use, Runnable remove) {
        int h = 26;
        Draw.rect(g, x, y, w, h, current ? 0xFF1C1C1F : 0xFF121214);
        Draw.outline(g, x, y, w, h, current ? 0xA0FFFFFF : 0x24FFFFFF);
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
        Draw.rect(g, x, y, w, h, hov ? 0xFFFFFFFF : 0xFF1A1A1C);
        Draw.outline(g, x, y, w, h, 0x40FFFFFF);
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2, hov ? 0xFF000000 : 0xFFD0D0D0);
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
