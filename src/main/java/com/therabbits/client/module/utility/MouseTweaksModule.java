package com.therabbits.client.module.utility;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;

/** Hold Shift and drag over slots to quick-move every item you pass (AbstractContainerScreenMixin). */
public class MouseTweaksModule extends Module {
    private static MouseTweaksModule instance;

    public MouseTweaksModule() { super("Mouse Tweaks", "Shift + drag over slots to move many stacks", Category.UTILITY, true); instance = this; }

    public static MouseTweaksModule get() { return instance; }
}
