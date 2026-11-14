package com.therabbits.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therabbits.client.cosmetics.CosmeticModel;
import com.therabbits.client.cosmetics.ShieldSkins;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShieldSpecialRenderer.class)
public class ShieldSpecialRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At("HEAD"), cancellable = true)
    private void therabbits$shieldSkin(DataComponentMap components, ItemDisplayContext ctx, PoseStack pose, SubmitNodeCollector out,
                                       int light, int overlay, boolean foil, int outline, CallbackInfo ci) {
        CosmeticModel skin = ShieldSkins.skinFor(ctx);
        if (skin == null) return;
        ShieldSkins.submit(skin, pose, out, light);
        ci.cancel();
    }
}
