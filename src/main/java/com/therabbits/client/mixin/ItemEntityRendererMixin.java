package com.therabbits.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.therabbits.client.module.visual.ItemPhysicsModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    /** Freeze the spin/bob (both come from age + bobOffset) and lay the item on its side. */
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void therabbits$physics(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera, CallbackInfo ci) {
        com.therabbits.client.module.visual.ItemCustomizerModule ic = com.therabbits.client.module.visual.ItemCustomizerModule.get();
        if (ic != null && ic.enabled() && ic.dropped.f() != 1) pose.scale(ic.dropped.f(), ic.dropped.f(), ic.dropped.f());
        ItemPhysicsModule m = ItemPhysicsModule.get();
        if (m == null || !m.enabled()) return;
        state.ageInTicks = 0;
        state.bobOffset = 0;
        if (m.flat.on()) {
            pose.translate(0, -0.12, 0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
        }
    }
}
