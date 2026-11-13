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
        CpsTracker.update();
        for (HudModule h : ModuleManager.hud()) {
            if (h.enabled()) draw(g, h, false);
        }
    }

    public static void draw(GuiGraphics g, HudModule h, boolean editing) {
        g.pose().pushMatrix();
        g.pose().translate((float) h.x, (float) h.y);
        g.pose().scale(h.scale.f(), h.scale.f());
        h.render(g, editing);
        g.pose().popMatrix();
    }
}
