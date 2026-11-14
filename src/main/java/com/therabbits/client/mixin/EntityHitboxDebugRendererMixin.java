package com.therabbits.client.mixin;

import com.therabbits.client.module.visual.HitboxesModule;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitbox filters (Hitboxes module). */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void therabbits$filter(Entity entity, float partialTick, boolean server, CallbackInfo ci) {
        HitboxesModule m = HitboxesModule.get();
        if (m != null && !m.shows(entity)) ci.cancel();
    }
}
