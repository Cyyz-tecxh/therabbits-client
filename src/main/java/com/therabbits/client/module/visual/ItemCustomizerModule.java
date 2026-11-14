package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.NumberSetting;

/** Item sizes: items in your hands, dropped items, and a lower shield. */
public class ItemCustomizerModule extends Module {
    private static ItemCustomizerModule instance;
    public final NumberSetting mainHand = add(new NumberSetting("Main Hand Size", 1, 0.3, 1.6, 0.05));
    public final NumberSetting offHand = add(new NumberSetting("Off Hand Size", 1, 0.3, 1.6, 0.05));
    public final NumberSetting dropped = add(new NumberSetting("Dropped Item Size", 1, 0.3, 3, 0.05));
    public final BoolSetting lowShield = add(new BoolSetting("Low Shield", false));
    public final NumberSetting shieldDrop = add(new NumberSetting("Low Shield Amount", 0.15, 0, 0.5, 0.01));

    public ItemCustomizerModule() { super("Item Customizer", "Held & dropped item size", Category.VISUAL, true); instance = this; }

    public static ItemCustomizerModule get() { return instance; }
}
