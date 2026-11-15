package com.therabbits.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therabbits.client.cosmetics.CosmeticsLayer;
import com.therabbits.client.module.cosmetics.WearablesModule;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.GuiSkinRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 3D player preview on the title, Cosmetics and Accounts screens only draws the skin.
 * Draw your masks, claws, aura and pet on it too, in the same model space, just before the batch is flushed.
 */
@Mixin(GuiSkinRenderer.class)
public abstract class GuiSkinRendererMixin extends PictureInPictureRenderer<GuiSkinRenderState> {
    protected GuiSkinRendererMixin(MultiBufferSource.BufferSource buffers) { super(buffers); }

    @Inject(method = "renderToTexture(Lnet/minecraft/client/gui/render/state/pip/GuiSkinRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private void therabbits$cosmetics(GuiSkinRenderState state, PoseStack pose, CallbackInfo ci) {
        try {
            CosmeticsLayer.drawAll(pose, CosmeticsLayer.of(this.bufferSource), 0xF000F0, state.playerModel(), null, 0f,
                    WearablesModule.ownFor("hat"), WearablesModule.ownFor("arms"),
                    WearablesModule.ownFor("aura"), WearablesModule.ownFor("pet"));
        } catch (Throwable ignored) {
            // a preview should never crash the menu
        }
    }
}
