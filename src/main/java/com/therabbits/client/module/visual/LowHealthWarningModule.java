package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/** Pulsing red screen edges when your health is low. Drawn by HudRenderer. */
public class LowHealthWarningModule extends Module {
    private final NumberSetting hearts = add(new NumberSetting("Below Hearts", 4, 1, 10, 0.5));

    public LowHealthWarningModule() { super("Low Health Warning", "Red edges at low health", Category.VISUAL, true); }

    public void draw(GuiGraphics g) {
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;
        float hp = mc.player.getHealth() / 2f;
        if (hp > hearts.get() || hp <= 0) return;
        float strength = 1f - hp / hearts.f();
        float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 300.0));
        int a = (int) (150 * (0.35f + 0.65f * strength) * pulse);
        int w = g.guiWidth(), h = g.guiHeight(), d = Math.max(20, h / 6);
        int red = 0xC00000;
        g.fillGradient(0, 0, w, d, (a << 24) | red, red);
        g.fillGradient(0, h - d, w, h, red, (a << 24) | red);
        for (int i = 0; i < d; i++) {
            int ai = (int) (a * (1 - i / (float) d));
            g.fill(i, 0, i + 1, h, (ai << 24) | red);
            g.fill(w - i - 1, 0, w - i, h, (ai << 24) | red);
        }
    }
}
