package com.therabbits.client.module.utility;

import com.therabbits.client.gui.PackCustomizerScreen;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.ActionSetting;

/** Turn resource packs on/off and reorder them from the TheRabbits menu. */
public class PackCustomizerModule extends Module {
    public PackCustomizerModule() {
        super("Pack Customizer", "Manage resource packs", Category.UTILITY, true);
        add(new ActionSetting("Resource Packs", "Open", () -> mc.setScreen(new PackCustomizerScreen(mc.screen))));
    }
}
