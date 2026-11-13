package com.therabbits.client.mixin;

import com.therabbits.client.module.hud.TotemPopModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    /** Entity event 35 = totem of undying used. RETURN only runs on the main thread pass. */
    @Inject(method = "handleEntityEvent", at = @At("RETURN"))
    private void therabbits$totem(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.getEventId() == 35 && mc.level != null && mc.isSameThread()) {
            Entity e = packet.getEntity(mc.level);
            if (e != null) TotemPopModule.onPop(e);
        }
    }
}
