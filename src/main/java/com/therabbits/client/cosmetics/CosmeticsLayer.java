package com.therabbits.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therabbits.client.module.cosmetics.WearablesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Draws TheRabbits hats and arm pieces on players (yours, and other TheRabbits players'). */
public class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final int FULL_BRIGHT = 0xF000F0;

    public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot, float xRot) {
        if (s.isInvisible || s.isSpectator) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(s.id);
        if (!(e instanceof Player p)) return;
        CosmeticModel hat = CosmeticModel.get(WearablesModule.hatFor(p));
        CosmeticModel arms = CosmeticModel.get(WearablesModule.armsFor(p));
        CosmeticModel aura = CosmeticModel.get(WearablesModule.auraFor(p));
        if (aura != null && "aura".equals(aura.attach)) drawAura(pose, out, aura);
        PlayerModel model = getParentModel();
        if (hat != null && !hat.arms) {
            pose.pushPose();
            model.head.translateAndRotate(pose);
            draw(pose, out, light, hat, hat.root);
            pose.popPose();
        }
        if (arms != null && arms.arms) {
            pose.pushPose();
            model.rightArm.translateAndRotate(pose);
            draw(pose, out, light, arms, arms.root);
            pose.popPose();
            pose.pushPose();
            model.leftArm.translateAndRotate(pose);
            draw(pose, out, light, arms, arms.mirrored);
            pose.popPose();
        }
    }

    /** Swirling rings (each spins its own way), crackling segments and rising sparks. */
    private static void drawAura(PoseStack pose, SubmitNodeCollector out, CosmeticModel m) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        ModelPart root = m.root;
        float[] speed = {2.4f, -1.8f, 3.0f};
        for (int k = 0; k < 3; k++) {
            if (!root.hasChild("ring" + k)) continue;
            ModelPart ring = root.getChild("ring" + k);
            var ip = ring.getInitialPose();
            ring.yRot = ip.yRot() + t * speed[k];
            ring.y = ip.y() + net.minecraft.util.Mth.sin(t * 2.1f + k * 1.7f) * 0.7f;
            int idx = 0;
            for (ModelPart seg : ring.getAllParts()) {
                if (seg == ring) continue;
                seg.visible = ((int) (t * 14) * 31 + idx * 17 + k * 7) % 6 != 0;   // crackle
                idx++;
            }
        }
        for (int i = 0; i < 16; i++) {
            if (!root.hasChild("p" + i)) continue;
            ModelPart p = root.getChild("p" + i);
            var ip = p.getInitialPose();
            float u = (t * 0.32f + i * 0.0625f) % 1f;
            p.y = ip.y() - u * 30f;
            p.x = ip.x() + net.minecraft.util.Mth.sin(t * 1.3f + i) * 0.8f;
            p.xScale = p.yScale = p.zScale = 1f - u * 0.7f;
        }
        out.submitModelPart(root, pose, RenderTypes.eyes(m.currentTexture()), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
    }

    public static void draw(PoseStack pose, SubmitNodeCollector out, int light, CosmeticModel m, ModelPart part) {
        out.submitModelPart(part, pose, RenderTypes.entityCutoutNoCull(m.currentTexture()), light, OverlayTexture.NO_OVERLAY, null);
        Identifier g = m.currentGlow();
        if (g != null)
            out.submitModelPart(part, pose, RenderTypes.eyes(g), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
    }
}
