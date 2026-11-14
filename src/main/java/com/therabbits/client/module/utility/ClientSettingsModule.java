package com.therabbits.client.module.utility;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;

/** General client options. */
public class ClientSettingsModule extends Module {
    private static ClientSettingsModule instance;

    public final BoolSetting animations = add(new BoolSetting("Menu Animations", true));
    public final BoolSetting hideHudInF3 = add(new BoolSetting("Hide HUD When F3 Is Open", true));

    public ClientSettingsModule() {
        super("Client Settings", "Menu animations, F3 behaviour and GUI scale", Category.UTILITY, true);
        instance = this;
    }

    public static boolean animations() { return instance == null || !instance.enabled() || instance.animations.on(); }
}
