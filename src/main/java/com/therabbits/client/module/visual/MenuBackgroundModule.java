package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.NumberSetting;

/** Settings for the start-menu background (always active; only its settings matter). */
public class MenuBackgroundModule extends Module {
    public static final String[] NAMES = {"Grid", "Starfield", "Rain", "Snow", "Plain"};

    public final NumberSetting style = add(new NumberSetting("Style (0 Grid, 1 Stars, 2 Rain, 3 Snow, 4 Plain)", 0, 0, NAMES.length - 1, 1));
    public final BoolSetting lightning = add(new BoolSetting("Lightning", true));
    public final BoolSetting glow = add(new BoolSetting("Glow", true));

    public MenuBackgroundModule() { super("Menu Background", "Start menu background style", Category.VISUAL, true); }

    public String styleName() { return NAMES[style.i()]; }
    public void next() { style.set((double) ((style.i() + 1) % NAMES.length)); }
}
