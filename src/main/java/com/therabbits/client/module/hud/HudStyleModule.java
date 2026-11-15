package com.therabbits.client.module.hud;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ColorSetting;
import com.therabbits.client.setting.NumberSetting;

/** Global look of all HUD text. Each HUD element can still override its own text color and size. */
public class HudStyleModule extends Module {
    public final ColorSetting textColor = add(new ColorSetting("Text Color", 0xFFFFFF, false, false));
    public final ColorSetting labelColor = add(new ColorSetting("Label Color", 0x909090, false, false));
    public final NumberSetting textSize = add(new NumberSetting("Text Size", 1.0, 0.5, 2.0, 0.05));
    public final BoolSetting shadow = add(new BoolSetting("Text Shadow", true));
    public final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", 0, 0, 100, 5));

    public HudStyleModule() { super("HUD Style", "HUD colour, size, shadow", Category.HUD, true); }
}
