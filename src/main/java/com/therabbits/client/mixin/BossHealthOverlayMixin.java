package com.therabbits.client.mixin;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.module.visual.HideBossBarModule;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void therabbits$hideBossBar(CallbackInfo ci) {
        if (ModuleManager.get(HideBossBarModule.class).enabled()) ci.cancel();
    }
}
