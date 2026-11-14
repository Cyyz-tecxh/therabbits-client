package com.therabbits.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therabbits.client.cosmetics.ShieldSkins;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers which player's hands are being drawn, so shield skins go on the right person. */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void therabbits$begin(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                                  PoseStack pose, SubmitNodeCollector out, int light, CallbackInfo ci) {
        ShieldSkins.holder = state instanceof AvatarRenderState a ? a.id : Integer.MAX_VALUE;
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void therabbits$end(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                                PoseStack pose, SubmitNodeCollector out, int light, CallbackInfo ci) {
        ShieldSkins.holder = Integer.MIN_VALUE;
    }
}
