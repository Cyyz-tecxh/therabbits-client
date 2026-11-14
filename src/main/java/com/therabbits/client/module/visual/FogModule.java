package com.therabbits.client.module.visual;

import com.therabbits.client.mixin.FogRendererAccessor;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import net.minecraft.client.renderer.fog.FogRenderer;

/** Turns world fog off while enabled. */
public class FogModule extends Module {
    public FogModule() { super("No Fog", "Remove fog", Category.VISUAL, false); }

    @Override
    public void onTick() { if (FogRendererAccessor.therabbits$fogEnabled()) FogRenderer.toggleFog(); }

    @Override
    protected void onDisable() { if (!FogRendererAccessor.therabbits$fogEnabled()) FogRenderer.toggleFog(); }
}
