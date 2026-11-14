package com.therabbits.client.mixin;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.module.combat.CrosshairModule;
import com.therabbits.client.module.visual.HideScoreboardModule;
import com.therabbits.client.module.hud.PotionEffectsModule;
import com.therabbits.client.module.visual.HideVignetteModule;
import com.therabbits.client.module.visual.TitleTweakerModule;
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

    @Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
    private void therabbits$titleStart(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        TitleTweakerModule t = TitleTweakerModule.get();
        if (t == null || !t.enabled()) return;
        if (t.hide.on()) { ci.cancel(); return; }
        float s = t.scale.f(), cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(s, s);
        g.pose().translate(-cx, -cy);
    }

    @Inject(method = "renderTitle", at = @At("RETURN"))
    private void therabbits$titleEnd(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        TitleTweakerModule t = TitleTweakerModule.get();
        if (t != null && t.enabled() && !t.hide.on()) g.pose().popMatrix();
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void therabbits$hideVignette(CallbackInfo ci) {
        if (ModuleManager.get(HideVignetteModule.class).enabled()) ci.cancel();
    }

    /** Hide vanilla's top-right effect icons when our potion HUD replaces them. */
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void therabbits$hideEffects(CallbackInfo ci) {
        PotionEffectsModule p = ModuleManager.get(PotionEffectsModule.class);
        if (p.enabled() && p.hideVanilla.on()) ci.cancel();
    }
}
