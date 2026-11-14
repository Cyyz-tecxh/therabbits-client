package com.therabbits.client.mixin;

import com.therabbits.client.emote.EmoteManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emote poses for your player model. */
@Mixin(PlayerModel.class)
public class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void therabbits$emote(AvatarRenderState state, CallbackInfo ci) {
        var e = EmoteManager.current();
        var me = Minecraft.getInstance().player;
        if (e == null || me == null || state.id != me.getId()) return;
        e.pose().apply((PlayerModel) (Object) this, EmoteManager.time(), EmoteManager.progress());
    }
}
