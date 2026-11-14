package com.therabbits.client.mixin;

import com.therabbits.client.module.utility.ClientSettingsModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Compact F3 screen (Client Settings -> F3 Screen). */
@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void therabbits$compact(GuiGraphics g, CallbackInfo ci) {
        if (!ClientSettingsModule.compactF3()) return;
        if (!((DebugScreenOverlay) (Object) this).showDebugScreen()) return;
        ClientSettingsModule.get().drawCompactF3(g);
        ci.cancel();
    }
}
