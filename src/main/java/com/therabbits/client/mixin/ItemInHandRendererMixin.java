package com.therabbits.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.therabbits.client.module.visual.ViewModelModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    /** Runs right after vanilla's pushPose for each hand, so the change stays inside that hand. */
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void therabbits$viewModel(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing,
                                      ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector nodes, int light, CallbackInfo ci) {
        com.therabbits.client.module.visual.ItemCustomizerModule ic = com.therabbits.client.module.visual.ItemCustomizerModule.get();
        if (ic != null && ic.enabled()) {
            if (ic.lowShield.on() && stack.is(net.minecraft.world.item.Items.SHIELD)) pose.translate(0, -ic.shieldDrop.f(), 0);
            float sz = hand == InteractionHand.MAIN_HAND ? ic.mainHand.f() : ic.offHand.f();
            if (sz != 1) pose.scale(sz, sz, sz);
        }
        ViewModelModule m = ViewModelModule.get();
        if (m == null || !m.enabled()) return;
        float side = hand == InteractionHand.MAIN_HAND ? 1 : -1;
        pose.translate(m.x.f() * side, m.y.f(), m.z.f());
        pose.mulPose(Axis.XP.rotationDegrees(m.rotX.f()));
        pose.mulPose(Axis.YP.rotationDegrees(m.rotY.f() * side));
        pose.mulPose(Axis.ZP.rotationDegrees(m.rotZ.f() * side));
        pose.scale(m.scale.f(), m.scale.f(), m.scale.f());
    }
}
