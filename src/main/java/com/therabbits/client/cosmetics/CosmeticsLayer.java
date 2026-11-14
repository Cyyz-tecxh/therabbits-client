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

    private static void draw(PoseStack pose, SubmitNodeCollector out, int light, CosmeticModel m, ModelPart part) {
        out.submitModelPart(part, pose, RenderTypes.entityCutoutNoCull(m.texture), light, OverlayTexture.NO_OVERLAY, null);
        if (m.glow != null)
            out.submitModelPart(part, pose, RenderTypes.eyes(m.glow), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
    }
}
