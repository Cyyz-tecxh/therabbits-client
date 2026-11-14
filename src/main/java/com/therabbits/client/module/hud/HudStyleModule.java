package com.therabbits.client.module.hud;

import com.therabbits.client.hud.HudColors;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;
import com.therabbits.client.setting.NumberSetting;

/** Global look of all HUD text. Each HUD element can still override its own text color and size. */
public class HudStyleModule extends Module {
    public final ModeSetting textColor = add(new ModeSetting("Text Color", 0, true, HudColors.NAMES));
    public final ModeSetting labelColor = add(new ModeSetting("Label Color", 2, true, HudColors.NAMES));
    public final NumberSetting textSize = add(new NumberSetting("Text Size", 1.0, 0.5, 2.0, 0.05));
    public final BoolSetting shadow = add(new BoolSetting("Text Shadow", true));
    public final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", 0, 0, 100, 5));

    public HudStyleModule() { super("HUD Style", "Text color, size, shadow and background for the HUD", Category.HUD, true); }
}
