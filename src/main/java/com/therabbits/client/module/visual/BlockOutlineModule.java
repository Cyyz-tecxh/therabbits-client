package com.therabbits.client.module.visual;

import com.therabbits.client.hud.HudColors;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.ModeSetting;
import com.therabbits.client.setting.NumberSetting;

/** Recolors and thickens the outline around the block you look at (LevelRendererMixin). */
public class BlockOutlineModule extends Module {
    private static BlockOutlineModule instance;
    public final ModeSetting color = add(new ModeSetting("Color", 0, true, HudColors.NAMES));
    public final NumberSetting opacity = add(new NumberSetting("Opacity %", 80, 10, 100, 5));
    public final NumberSetting width = add(new NumberSetting("Thickness", 3, 1, 8, 0.5));

    public BlockOutlineModule() { super("Block Outline", "Block outline colour", Category.VISUAL, true); instance = this; }

    public static BlockOutlineModule get() { return instance; }

    public int argb() { return ((int) Math.round(opacity.get() * 2.55) << 24) | HudColors.rgb(color.get(), 0); }
}
