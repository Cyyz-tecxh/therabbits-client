package com.therabbits.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therabbits.client.cosmetics.CosmeticModel;
import com.therabbits.client.cosmetics.CosmeticsLayer;
import com.therabbits.client.module.cosmetics.WearablesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arm cosmetics (claws) on your hands in first person. */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(method = "renderRightHand", at = @At("RETURN"))
    private void therabbits$rightHand(PoseStack pose, SubmitNodeCollector out, int light, Identifier skin, boolean sleeve, CallbackInfo ci) {
        draw(pose, out, light, false);
    }

    @Inject(method = "renderLeftHand", at = @At("RETURN"))
    private void therabbits$leftHand(PoseStack pose, SubmitNodeCollector out, int light, Identifier skin, boolean sleeve, CallbackInfo ci) {
        draw(pose, out, light, true);
    }

    private void draw(PoseStack pose, SubmitNodeCollector out, int light, boolean left) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        CosmeticModel arms = CosmeticModel.get(WearablesModule.armsFor(player));
        if (arms == null || !arms.arms) return;
        PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
        pose.pushPose();
        (left ? model.leftArm : model.rightArm).translateAndRotate(pose);
        CosmeticsLayer.draw(pose, out, light, arms, left ? arms.mirrored : arms.root);
        pose.popPose();
    }
}
