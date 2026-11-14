package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;

/** Dropped items stop spinning and bobbing and lie flat on the ground (ItemEntityRendererMixin). */
public class ItemPhysicsModule extends Module {
    private static ItemPhysicsModule instance;
    public final BoolSetting flat = add(new BoolSetting("Lie Flat", true));

    public ItemPhysicsModule() { super("Item Physics", "Dropped items lie flat instead of spinning", Category.VISUAL, false); instance = this; }

    public static ItemPhysicsModule get() { return instance; }
}
