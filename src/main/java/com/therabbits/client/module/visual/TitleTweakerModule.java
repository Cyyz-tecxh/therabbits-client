package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.NumberSetting;

/** Scales or hides the big server titles (GuiMixin). */
public class TitleTweakerModule extends Module {
    private static TitleTweakerModule instance;
    public final NumberSetting scale = add(new NumberSetting("Title Scale", 0.6, 0.2, 1.5, 0.05));
    public final BoolSetting hide = add(new BoolSetting("Hide Titles", false));

    public TitleTweakerModule() { super("Title Tweaker", "Shrink or hide big on-screen titles", Category.VISUAL, false); instance = this; }

    public static TitleTweakerModule get() { return instance; }
}
