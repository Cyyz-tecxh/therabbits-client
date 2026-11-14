package com.therabbits.client.mixin;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.module.combat.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.therabbits.client.module.visual.NoHurtCamModule;
import com.therabbits.client.module.visual.NoTotemAnimationModule;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void therabbits$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        double f = ModuleManager.get(ZoomModule.class).factor();
        if (f != 1) cir.setReturnValue((float) (cir.getReturnValue() / f));
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void therabbits$noTotemAnimation(CallbackInfo ci) {
        if (ModuleManager.get(NoTotemAnimationModule.class).enabled()) ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void therabbits$noHurtCam(CallbackInfo ci) {
        if (ModuleManager.get(NoHurtCamModule.class).enabled()) ci.cancel();
    }
}
