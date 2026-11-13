package com.therabbits.client.mixin;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.module.combat.CrosshairModule;
import com.therabbits.client.module.visual.HideScoreboardModule;
import com.therabbits.client.module.hud.PotionEffectsModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void therabbits$crosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        CrosshairModule c = ModuleManager.get(CrosshairModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (c.enabled() && mc.options.getCameraType().isFirstPerson()) {
            c.draw(g);
            ci.cancel();
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void therabbits$hideScoreboard(CallbackInfo ci) {
        if (ModuleManager.get(HideScoreboardModule.class).enabled()) ci.cancel();
    }

    /** Hide vanilla's top-right effect icons when our potion HUD replaces them. */
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void therabbits$hideEffects(CallbackInfo ci) {
        PotionEffectsModule p = ModuleManager.get(PotionEffectsModule.class);
        if (p.enabled() && p.hideVanilla.on()) ci.cancel();
    }
}
