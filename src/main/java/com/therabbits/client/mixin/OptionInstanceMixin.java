package com.therabbits.client.mixin;

import com.therabbits.client.module.visual.FullbrightModule;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.class)
public class OptionInstanceMixin<T> {
    @SuppressWarnings("unchecked")
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void therabbits$fullbright(CallbackInfoReturnable<T> cir) {
        if (FullbrightModule.isActiveGamma(this)) cir.setReturnValue((T) (Object) 15.0);
    }
}
