package com.therabbits.client.gui;

import com.therabbits.client.account.Account;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shown instead of the menu when the server owner requires a license key and you haven't activated one. */
public class LicenseScreen extends Screen {
    private EditBox key;
    private Button activate;
    private String status = "";
    private boolean working;

    public LicenseScreen() { super(Component.literal("TheRabbits License")); }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        key = new EditBox(font, cx - 110, cy - 4, 220, 20, Component.literal("License key"));
        key.setMaxLength(40);
        key.setHint(Component.literal("TRLH-XXXX-XXXX-XXXX"));
        addRenderableWidget(key);
        activate = addRenderableWidget(Button.builder(Component.literal("Activate"), b -> activate()).bounds(cx - 110, cy + 24, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Quit Game"), b -> minecraft.stop()).bounds(cx + 4, cy + 24, 106, 20).build());
        setInitialFocus(key);
    }

    private void activate() {
        if (working) return;
        if (key.getValue().isBlank()) { status = "Enter your license key"; return; }
        working = true;
        activate.active = false;
        status = "Checking...";
        Account.redeem(key.getValue()).thenAccept(msg -> minecraft.execute(() -> {
            working = false;
            activate.active = true;
            if (Account.licensed) minecraft.setScreen(new RabbitsTitleScreen());
            else status = msg;
        }));
    }

    @Override
    public void tick() {
        // the owner may have switched license keys off, or you activated on another device
        if (Account.configLoaded && (!Account.licenseRequired || Account.licensed)) minecraft.setScreen(new RabbitsTitleScreen());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xFF0B0B0D);
        int cx = width / 2, cy = height / 2;
        Draw.logo(g, cx - 24, cy - 112, 48);
        Draw.centered(g, "TheRabbits Client", cx, cy - 58, 0xFFFFFFFF);
        Draw.centered(g, "Enter your license key to start playing.", cx, cy - 40, 0xFF9A9A9A);
        Draw.centered(g, "Get a key from the TheRabbits team.", cx, cy - 28, 0xFF6A6A6A);
        super.render(g, mx, my, delta);
        if (!status.isEmpty()) Draw.centered(g, status, cx, cy + 54, status.startsWith("License") ? 0xFF6AD48A : 0xFFFF7A7A);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
