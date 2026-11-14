package com.therabbits.client.gui;

import com.therabbits.client.Config;
import com.therabbits.client.ModuleManager;
import com.therabbits.client.account.Account;
import com.therabbits.client.cosmetics.Capes;
import com.therabbits.client.cosmetics.Wearables;
import com.therabbits.client.mixin.PlayerSkinWidgetAccessor;
import com.therabbits.client.module.cosmetics.AccountModule;
import com.therabbits.client.module.cosmetics.CapesModule;
import com.therabbits.client.module.cosmetics.WearablesModule;
import com.therabbits.client.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Cosmetics locker: tabs for capes / hats / arms / shields / codes, picture cards, live player preview. */
public class CosmeticsScreen extends Screen {
    private enum Tab {
        CAPES("Capes"), HATS("Hats"), ARMS("Arms"), SHIELDS("Shields"), AURAS("Auras"), CODES("Online & Codes");
        final String label;
        Tab(String l) { label = l; }
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final Screen parent;
    private Tab tab = Tab.HATS;
    private PlayerSkinWidget preview;
    private net.minecraft.client.gui.components.EditBox search;
    private final List<Hit> hits = new ArrayList<>();
    private double scroll;
    private String toast;
    private long toastAt;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
    }

    private int panelX() { return 10; }
    private int panelW() { return Math.max(110, width / 4); }
    private int gridX() { return panelX() + panelW() + 12; }
    private int gridW() { return width - gridX() - 12; }

    @Override
    protected void init() {
        int pw = panelW() - 20, ph = Math.min(height - 90, (int) (pw * 1.9));
        preview = new PlayerSkinWidget(pw, ph, minecraft.getEntityModels(), SkinDraw::skin);
        preview.setPosition(panelX() + 10, 44 + (height - 80 - ph) / 2);
        addRenderableWidget(preview);
        search = new net.minecraft.client.gui.components.EditBox(font, gridX(), 38, Math.min(200, gridW()), 14, Component.literal("Search"));
        search.setHint(Component.literal("Search cosmetics..."));
        addRenderableWidget(search);
        Account.refreshNow();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        if (minecraft.level == null) super.renderBackground(g, mx, my, delta);
        g.fill(0, 0, width, height, 0xB80A0A0C);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        // header
        Draw.logo(g, 12, 8, 22);
        Draw.text(g, "COSMETICS", 40, 12, 0xFFFFFFFF);
        Draw.text(g, "Your look", 40, 23, 0xFF7A7A7A);
        int tx = gridX();
        for (Tab t : Tab.values()) {
            int w = Draw.width(t.label) + 18;
            boolean sel = t == tab, hov = Draw.inside(mx, my, tx, 10, w, 18);
            Draw.rect(g, tx, 10, w, 18, sel ? Theme.accent() : hov ? 0xFF262626 : 0xFF151515);
            if (!sel) Draw.outline(g, tx, 10, w, 18, 0x30FFFFFF);
            Draw.centered(g, t.label, tx + w / 2, 15, sel ? Theme.onAccent() : 0xFFD0D0D0);
            hits.add(new Hit(tx, 10, w, 18, () -> { tab = t; scroll = 0; if (t == Tab.CODES) openCodes(); }));
            tx += w + 4;
        }
        button(g, mx, my, width - 62, 10, 50, 18, "Mods", () -> minecraft.setScreen(new ModMenuScreen(parent)));

        // preview panel
        int px = panelX(), pw = panelW(), py = 40, ph = height - 52;
        Draw.rect(g, px, py, pw, ph, 0xC0121214);
        Draw.outline(g, px, py, pw, ph, 0x30FFFFFF);
        Draw.centered(g, minecraft.getUser().getName(), px + pw / 2, py + 6, 0xFFEDEDED);
        PlayerSkinWidgetAccessor acc = (PlayerSkinWidgetAccessor) preview;
        if (!preview.isHovered()) acc.therabbits$setRotationY(acc.therabbits$getRotationY() + delta * 0.6f);
        super.render(g, mx, my, delta);
        String worn = wornLine();
        Draw.centered(g, Draw.trim(worn, pw - 8), px + pw / 2, py + ph - 14, 0xFF8A8A8A);

        // grid
        int gx = gridX(), gy = 58, gw = gridW(), gh = height - 70;
        List<Item> items = items();
        int cw = 74, ch = 92, gap = 8, cols = Math.max(1, (gw + gap) / (cw + gap));
        int rows = (items.size() + cols - 1) / cols;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows * (ch + gap) - gh));
        g.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int x = gx + (i % cols) * (cw + gap), y = gy + (i / cols) * (ch + gap) - (int) scroll;
            if (y + ch < gy || y > gy + gh) continue;
            card(g, it, x, y, cw, ch, mx, my, gy, gh);
        }
        g.disableScissor();
        if (items.isEmpty()) Draw.centered(g, "Opening codes...", gx + gw / 2, gy + 40, 0xFF8A8A8A);
        if (toast != null && System.currentTimeMillis() - toastAt < 2200) {
            int w = Draw.width(toast) + 20;
            Draw.rect(g, width / 2 - w / 2, height - 30, w, 18, 0xF0FFFFFF);
            Draw.centered(g, toast, width / 2, height - 25, 0xFF000000);
        }
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, Runnable r) {
        boolean hov = Draw.inside(mx, my, x, y, w, h);
        Draw.rect(g, x, y, w, h, hov ? Theme.accent() : 0xFF151515);
        Draw.outline(g, x, y, w, h, 0x40FFFFFF);
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2, hov ? Theme.onAccent() : 0xFFD0D0D0);
        hits.add(new Hit(x, y, w, h, r));
    }

    // ---------------------------------------------------------------- items
    private record Item(String id, String name, ModeSetting setting, int index, boolean cape) {}

    private List<Item> items() {
        List<Item> out = new ArrayList<>();
        WearablesModule w = ModuleManager.get(WearablesModule.class);
        CapesModule c = ModuleManager.get(CapesModule.class);
        switch (tab) {
            case CAPES -> { for (int i = 0; i < Capes.IDS.length; i++) out.add(new Item(Capes.IDS[i], Capes.NAMES[i], c.cape, i, true)); }
            case HATS -> { for (int i = 0; i < Wearables.HAT_IDS.length; i++) out.add(new Item(Wearables.HAT_IDS[i], Wearables.HAT_NAMES[i], w.hat, i, false)); }
            case ARMS -> { for (int i = 0; i < Wearables.ARM_IDS.length; i++) out.add(new Item(Wearables.ARM_IDS[i], Wearables.ARM_NAMES[i], w.arms, i, false)); }
            case SHIELDS -> { for (int i = 0; i < Wearables.SHIELD_IDS.length; i++) out.add(new Item(Wearables.SHIELD_IDS[i], Wearables.SHIELD_NAMES[i], w.shield, i, false)); }
            case AURAS -> { for (int i = 0; i < Wearables.AURA_IDS.length; i++) out.add(new Item(Wearables.AURA_IDS[i], Wearables.AURA_NAMES[i], w.aura, i, false)); }
            case CODES -> {}
        }
        String q = search == null ? "" : search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) out.removeIf(i -> !i.name().toLowerCase(java.util.Locale.ROOT).contains(q));
        return out;
    }

    private void card(GuiGraphics g, Item it, int x, int y, int w, int h, int mx, int my, int areaY, int areaH) {
        boolean sel = it.setting().get() == it.index();
        boolean locked = Account.isLocked(it.id());
        boolean hov = Draw.inside(mx, my, x, y, w, h) && my >= areaY && my < areaY + areaH;
        Draw.rect(g, x, y, w, h, hov ? 0xF0202024 : 0xF0141417);
        Draw.outline(g, x, y, w, h, sel ? Theme.accent() : hov ? 0x70FFFFFF : 0x28FFFFFF);
        if (sel) Draw.outline(g, x + 1, y + 1, w - 2, h - 2, Theme.accent(0x60));
        int ix = x + 5, iy = y + 5, is = w - 10;
        if (it.id().equals("none")) {
            Draw.centered(g, "-", x + w / 2, iy + is / 2 - 4, 0xFF6A6A6A);
        } else if (it.cape()) {
            // front of the cape (vanilla layout: 10x16 px at (1,1), texture is 64x32 at 16x)
            Identifier tex = Identifier.fromNamespaceAndPath("therabbits", "textures/capes/" + it.id() + "/" + ((System.currentTimeMillis() / 125) % 16) + ".png");
            int cwid = is * 10 / 16 * 9 / 10, chei = is * 9 / 10;
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w / 2 - cwid / 2, iy + (is - chei) / 2, 16, 16, cwid, chei, 160, 256, 1024, 512);
        } else {
            Identifier icon = Identifier.fromNamespaceAndPath("therabbits", "textures/gui/cosmetic_icons/" + it.id() + ".png");
            g.blit(RenderPipelines.GUI_TEXTURED, icon, ix, iy, 0, 0, is, is, 128, 128, 128, 128);
        }
        if (locked) {
            g.fill(x + 1, y + 1, x + w - 1, y + is + 6, 0x90000000);
            Draw.centered(g, "LOCKED", x + w / 2, iy + is / 2 - 4, 0xFFFFFFFF);
        }
        Draw.centered(g, Draw.trim(it.name(), w - 6), x + w / 2, y + h - 22, 0xFFEDEDED);
        Draw.centered(g, sel ? "Equipped" : locked ? "Redeem a code" : "Free", x + w / 2, y + h - 11, sel ? Theme.accent() : 0xFF6A6A6A);
        int top = Math.max(y, areaY), bottom = Math.min(y + h, areaY + areaH);
        if (bottom > top) hits.add(new Hit(x, top, w, bottom - top, () -> select(it, locked)));
    }

    private void select(Item it, boolean locked) {
        if (locked) { toast("Locked - redeem a code in the Codes tab"); return; }
        it.setting().set(it.index());
        if (it.cape()) ModuleManager.get(CapesModule.class).setEnabled(true);
        else ModuleManager.get(WearablesModule.class).setEnabled(true);
        Config.save();
        toast(it.id().equals("none") ? "Removed" : "Equipped " + it.name());
    }

    private String wornLine() {
        WearablesModule w = ModuleManager.get(WearablesModule.class);
        CapesModule c = ModuleManager.get(CapesModule.class);
        List<String> parts = new ArrayList<>();
        if (!c.capeId().equals("none")) parts.add(Capes.NAMES[c.cape.get()]);
        if (!w.hatId().equals("none")) parts.add(Wearables.HAT_NAMES[w.hat.get()]);
        if (!w.armsId().equals("none")) parts.add(Wearables.ARM_NAMES[w.arms.get()]);
        if (!w.shieldId().equals("none")) parts.add(Wearables.SHIELD_NAMES[w.shield.get()]);
        if (!w.auraId().equals("none")) parts.add(Wearables.AURA_NAMES[w.aura.get()]);
        return parts.isEmpty() ? "Nothing equipped" : String.join(" · ", parts);
    }

    private void openCodes() {
        ModMenuScreen menu = new ModMenuScreen(this);
        minecraft.setScreen(menu);
        menu.openSettings(ModuleManager.get(AccountModule.class));
        tab = Tab.HATS;
    }

    private void toast(String s) { toast = s; toastAt = System.currentTimeMillis(); }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            if (search.mouseClicked(e, dbl)) return true;
            search.setFocused(false);
            for (Hit h : List.copyOf(hits))
                if (Draw.inside(e.x(), e.y(), h.x(), h.y(), h.w(), h.h())) { h.action().run(); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - sy * 24);
        return true;
    }

    @Override
    public void onClose() { Config.save(); minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
