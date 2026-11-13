package com.therabbits.client.mixin;

import com.therabbits.client.module.utility.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void therabbits$freelook(double dx, double dy, CallbackInfo ci) {
        FreelookModule f = FreelookModule.get();
        if (f != null && f.active() && (Object) this == Minecraft.getInstance().player) {
            f.turn(dx, dy);
            ci.cancel();
        }
    }
}
