package com.therabbits.client.mixin;

import com.therabbits.client.module.cosmetics.CapesModule;
import com.therabbits.client.skin.SkinChanger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Your chosen skin (only you see it) and TheRabbits capes (synced between TheRabbits players). */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void therabbits$skinAndCape(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        boolean me = self == Minecraft.getInstance().player;
        PlayerSkin skin = cir.getReturnValue();
        if (me) {
            PlayerSkin chosen = SkinChanger.override();
            if (chosen != null) skin = chosen;
        }
        ClientAsset.Texture cape = me ? CapesModule.ownCape() : CapesModule.capeFor(self.getUUID());
        if (cape != null) skin = new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure());
        if (skin != cir.getReturnValue()) cir.setReturnValue(skin);
    }
}
