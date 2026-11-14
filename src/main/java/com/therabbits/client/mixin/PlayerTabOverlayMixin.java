package com.therabbits.client.mixin;

import com.therabbits.client.module.utility.AltHiderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void therabbits$altHider(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (AltHiderModule.active() && mc.player != null && info.getProfile().id().equals(mc.player.getUUID()))
            cir.setReturnValue(Component.literal(AltHiderModule.hide(cir.getReturnValue().getString())));
    }
}
