package com.therabbits.client.mixin;

import com.therabbits.client.module.visual.TimeWeatherModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather module: only changes what your client shows (never the server / singleplayer world). */
@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
    private void therabbits$time(CallbackInfoReturnable<Long> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        TimeWeatherModule m = TimeWeatherModule.get();
        if (m == null) return;
        long t = m.dayTime(cir.getReturnValue());
        if (t >= 0) cir.setReturnValue(t);
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void therabbits$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        TimeWeatherModule m = TimeWeatherModule.get();
        if (m == null) return;
        float r = m.rain();
        if (r >= 0) cir.setReturnValue(r);
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void therabbits$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        TimeWeatherModule m = TimeWeatherModule.get();
        if (m == null) return;
        float r = m.thunder();
        if (r >= 0) cir.setReturnValue(r);
    }
}
