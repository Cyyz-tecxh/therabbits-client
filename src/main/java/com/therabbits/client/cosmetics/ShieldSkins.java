package com.therabbits.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therabbits.client.module.cosmetics.WearablesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;

/** Replaces the vanilla shield with a TheRabbits shield skin for players who picked one. */
public final class ShieldSkins {
    private ShieldSkins() {}

    /** Entity id of the player whose held items are being drawn right now (set by ItemInHandLayerMixin). */
    public static int holder = Integer.MIN_VALUE;

    /** The skin to use for a shield drawn in this context, or null for the vanilla look. */
    public static CosmeticModel skinFor(ItemDisplayContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (holder != Integer.MIN_VALUE) {
            if (mc.level == null) return null;
            Entity e = mc.level.getEntity(holder);
            return e instanceof Player p ? CosmeticModel.get(WearablesModule.shieldFor(p)) : null;
        }
        // your own hands in first person, and your own inventory/hotbar
        if (ctx.firstPerson() || ctx == ItemDisplayContext.GUI) return CosmeticModel.get(WearablesModule.ownShield());
        return null;
    }

    /** Draws the skin where the vanilla shield model would be. */
    public static void submit(CosmeticModel m, PoseStack pose, SubmitNodeCollector out, int light) {
        pose.pushPose();
        pose.scale(1.0F, -1.0F, -1.0F);
        pose.translate(0.0F, 0.0F, -1.5F / 16.0F);
        CosmeticsLayer.draw(pose, out, light, m, m.root);
        pose.popPose();
    }
}
