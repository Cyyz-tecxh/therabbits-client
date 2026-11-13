package com.therabbits.client.mixin;

import com.therabbits.client.skin.SkinChanger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shows the chosen skin on your own player (only on your screen). */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void therabbits$skin(CallbackInfoReturnable<PlayerSkin> cir) {
        if ((Object) this == Minecraft.getInstance().player) {
            PlayerSkin s = SkinChanger.override();
            if (s != null) cir.setReturnValue(s);
        }
    }
}
