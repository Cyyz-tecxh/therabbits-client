package com.therabbits.client.mixin;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.module.visual.HideFireModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void therabbits$hideFire(CallbackInfo ci) {
        if (ModuleManager.get(HideFireModule.class).enabled()) ci.cancel();
    }
}
