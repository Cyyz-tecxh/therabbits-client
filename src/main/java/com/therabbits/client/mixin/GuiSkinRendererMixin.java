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
 * Draw your cape, masks, claws, aura and pet on it too, in the same model space, just before the batch is flushed.
 */
@Mixin(GuiSkinRenderer.class)
public abstract class GuiSkinRendererMixin extends PictureInPictureRenderer<GuiSkinRenderState> {
    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.client.model.geom.ModelPart therabbits$capeModel;

    /** Same shape and place as the vanilla cape: 10x16x1 behind the body, hanging with a slight tilt. */
    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.client.model.geom.ModelPart therabbits$cape() {
        if (therabbits$capeModel == null) {
            var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
            mesh.getRoot().addOrReplaceChild("cape",
                    net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 0).addBox(-5f, 0f, -1f, 10f, 16f, 1f),
                    net.minecraft.client.model.geom.PartPose.offsetAndRotation(0f, 0f, 2.45f, (float) Math.toRadians(9), (float) Math.PI, 0f));
            therabbits$capeModel = net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 32).bakeRoot();
        }
        return therabbits$capeModel;
    }

    protected GuiSkinRendererMixin(MultiBufferSource.BufferSource buffers) { super(buffers); }

    @Inject(method = "renderToTexture(Lnet/minecraft/client/gui/render/state/pip/GuiSkinRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private void therabbits$cosmetics(GuiSkinRenderState state, PoseStack pose, CallbackInfo ci) {
        // only the Cosmetics screen shows them for now (title screen and Accounts show just your skin)
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof com.therabbits.client.gui.CosmeticsScreen)) return;
        try {   // your cape (vanilla previews never draw one)
            net.minecraft.core.ClientAsset.Texture cape = com.therabbits.client.module.cosmetics.CapesModule.ownCape();
            if (cape != null) {
                net.minecraft.client.model.geom.ModelPart c = therabbits$cape();
                CosmeticsLayer.of(this.bufferSource).part(c, pose,
                        net.minecraft.client.renderer.rendertype.RenderTypes.entitySolid(cape.texturePath()), 0xF000F0);
            }
        } catch (Throwable ignored) {}
        try {
            CosmeticsLayer.drawAll(pose, CosmeticsLayer.of(this.bufferSource), 0xF000F0, state.playerModel(), null, 0f,
                    WearablesModule.ownFor("hat"), WearablesModule.ownFor("arms"),
                    WearablesModule.ownFor("aura"), WearablesModule.ownFor("pet"));
        } catch (Throwable ignored) {
            // a preview should never crash the menu
        }
    }
}
