package com.therabbits.client.mixin;

import com.therabbits.client.module.utility.FreelookModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    /** After vanilla aims the camera from the player, aim it at the freelook angles instead. */
    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", shift = At.Shift.AFTER))
    private void therabbits$freelook(CallbackInfo ci) {
        FreelookModule f = FreelookModule.get();
        if (f != null && f.active()) setRotation(f.yaw(), f.pitch());
    }
}
