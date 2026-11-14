package com.therabbits.client.hud;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.gui.HudEditorScreen;
import com.therabbits.client.module.HudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class HudRenderer {
    private HudRenderer() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (mc.screen instanceof HudEditorScreen) return; // the editor draws them itself
        var cs = ModuleManager.get(com.therabbits.client.module.utility.ClientSettingsModule.class);
        if (cs.enabled() && cs.hideHudInF3.on() && mc.getDebugOverlay().showDebugScreen()) return;
        CpsTracker.update();
        var low = ModuleManager.get(com.therabbits.client.module.visual.LowHealthWarningModule.class);
        if (low.enabled()) low.draw(g);
        for (HudModule h : ModuleManager.hud()) {
            if (h.enabled()) draw(g, h, false);
        }
    }

    public static void draw(GuiGraphics g, HudModule h, boolean editing) {
        // keep elements on screen if the window is small, without changing the saved position
        double x = Math.max(0, Math.min(h.x, g.guiWidth() - h.scaledWidth()));
        double y = Math.max(0, Math.min(h.y, g.guiHeight() - h.scaledHeight()));
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(h.effectiveScale(), h.effectiveScale());
        HudStyle.current = h;
        com.therabbits.client.gui.Draw.hudContext = true;
        h.render(g, editing);
        com.therabbits.client.gui.Draw.hudContext = false;
        HudStyle.current = null;
        g.pose().popMatrix();
    }
}
