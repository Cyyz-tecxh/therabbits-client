package com.therabbits.client.module.utility;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;

/** Inventory mouse shortcuts (AbstractContainerScreenMixin). */
public class MouseTweaksModule extends Module {
    private static MouseTweaksModule instance;

    public final BoolSetting shiftDrag = add(new BoolSetting("Shift + Drag Moves Stacks", true));
    public final BoolSetting wheel = add(new BoolSetting("Scroll Wheel Moves Stacks", true));
    public final ModeSetting wheelDir = add(new ModeSetting("Wheel Direction", 0, false, "Any", "Down only", "Up only"));

    public MouseTweaksModule() { super("Mouse Tweaks", "Faster inventory moves", Category.UTILITY, true); instance = this; }

    public static MouseTweaksModule get() { return instance; }

    public boolean wheelMoves(double scrollY) {
        if (!enabled() || !wheel.on() || scrollY == 0) return false;
        return switch (wheelDir.get()) { case 1 -> scrollY < 0; case 2 -> scrollY > 0; default -> true; };
    }
}
