package com.therabbits.client.module.utility;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.TextSetting;
import net.minecraft.client.Minecraft;

/** Shows a fake name instead of your username in the tab list, game messages and menus. */
public class AltHiderModule extends Module {
    private static AltHiderModule instance;
    public final TextSetting alias = add(new TextSetting("Show As", "Player", 16));

    public AltHiderModule() { super("Alt Hider", "Hide your username", Category.UTILITY, false); instance = this; }

    public static boolean active() { return instance != null && instance.enabled() && !instance.alias.get().isBlank(); }

    /** Your name as it should be shown. */
    public static String displayName() {
        String real = Minecraft.getInstance().getUser().getName();
        return active() ? instance.alias.get() : real;
    }

    public static String hide(String text) {
        if (!active()) return text;
        return text.replace(Minecraft.getInstance().getUser().getName(), instance.alias.get());
    }
}
