package com.therabbits.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therabbits.client.emote.EmoteManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Whole-body emote moves (backflip, spin, jumps). */
@Mixin(AvatarRenderer.class)
public class AvatarRendererEmoteMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("RETURN"))
    private void therabbits$emoteMove(AvatarRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
        var e = EmoteManager.current();
        var me = Minecraft.getInstance().player;
        if (e == null || e.move() == null || me == null || state.id != me.getId()) return;
        e.move().apply(pose, EmoteManager.time(), EmoteManager.progress());
    }
}
