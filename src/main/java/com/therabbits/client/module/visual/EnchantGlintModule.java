package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.NumberSetting;

/** Glint speed and strength (0 strength hides the glint). */
public class EnchantGlintModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed %", 50, 0, 100, 5));
    private final NumberSetting strength = add(new NumberSetting("Strength %", 75, 0, 100, 5));
    private double oldSpeed = -1, oldStrength = -1;

    public EnchantGlintModule() { super("Enchant Glint", "Change glint speed and strength", Category.VISUAL, false); }

    @Override
    protected void onEnable() {
        oldSpeed = mc.options.glintSpeed().get();
        oldStrength = mc.options.glintStrength().get();
    }

    @Override
    public void onTick() {
        mc.options.glintSpeed().set(speed.get() / 100.0);
        mc.options.glintStrength().set(strength.get() / 100.0);
    }

    @Override
    protected void onDisable() {
        if (oldSpeed >= 0) { mc.options.glintSpeed().set(oldSpeed); mc.options.glintStrength().set(oldStrength); }
    }
}
