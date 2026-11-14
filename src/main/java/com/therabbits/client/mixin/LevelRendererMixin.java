package com.therabbits.client.mixin;

import com.therabbits.client.module.visual.BlockOutlineModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int therabbits$outlineColor(int color) {
        BlockOutlineModule m = BlockOutlineModule.get();
        return m != null && m.enabled() ? m.argb() : color;
    }

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float therabbits$outlineWidth(float width) {
        BlockOutlineModule m = BlockOutlineModule.get();
        return m != null && m.enabled() ? m.width.f() : width;
    }
}
