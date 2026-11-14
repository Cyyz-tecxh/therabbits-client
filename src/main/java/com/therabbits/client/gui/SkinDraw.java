package com.therabbits.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Draws the player's skin as a flat front-facing figure (32 skin pixels tall). */
public final class SkinDraw {
    private SkinDraw() {}

    public static PlayerSkin skin() {
        PlayerSkin base = baseSkin();
        var cape = com.therabbits.client.module.cosmetics.CapesModule.ownCape();
        return cape == null ? base : new PlayerSkin(base.body(), cape, cape, base.model(), base.secure());
    }

    private static PlayerSkin baseSkin() {
        PlayerSkin chosen = com.therabbits.client.skin.SkinChanger.override();
        if (chosen != null) return chosen;
        Minecraft mc = Minecraft.getInstance();
        return mc.getSkinManager().createLookup(mc.getGameProfile(), false).get();
    }

    /**
     * @param x left of the figure, y top of the head
     * @param px screen pixels per skin pixel (figure is 16*px wide, 32*px tall)
     * @param swing arm/leg swing in skin pixels (small idle animation)
     */
    public static void draw(GuiGraphics g, int x, int y, int px, float swing) {
        PlayerSkin skin = skin();
        Identifier tex = skin.body().texturePath();
        boolean slim = skin.model() == PlayerModelType.SLIM;
        int aw = slim ? 3 : 4;
        int s = Math.round(swing);

        int bodyX = x + 4 * px, armR = x + (4 - aw) * px, armL = x + 12 * px;
        // legs
        part(g, tex, bodyX, y + 20 * px + s, 4, 20, 4, 12, px);          // right leg
        part(g, tex, bodyX + 4 * px, y + 20 * px - s, 20, 52, 4, 12, px); // left leg
        part(g, tex, bodyX, y + 20 * px + s, 4, 36, 4, 12, px);          // pants
        part(g, tex, bodyX + 4 * px, y + 20 * px - s, 4, 52, 4, 12, px);
        // body
        part(g, tex, bodyX, y + 8 * px, 20, 20, 8, 12, px);
        part(g, tex, bodyX, y + 8 * px, 20, 36, 8, 12, px);              // jacket
        // arms
        part(g, tex, armR, y + 8 * px - s, 44, 20, aw, 12, px);
        part(g, tex, armL, y + 8 * px + s, 36, 52, aw, 12, px);
        part(g, tex, armR, y + 8 * px - s, 44, 36, aw, 12, px);          // sleeves
        part(g, tex, armL, y + 8 * px + s, 52, 52, aw, 12, px);
        // head + hat layer
        part(g, tex, x + 4 * px, y, 8, 8, 8, 8, px);
        part(g, tex, x + 4 * px, y, 40, 8, 8, 8, px);
    }

    private static void part(GuiGraphics g, Identifier tex, int x, int y, int u, int v, int w, int h, int px) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w * px, h * px, w, h, 64, 64);
    }
}
