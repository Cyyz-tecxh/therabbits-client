package com.therabbits.client.module.combat;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;

/** Keeps the sprint key held so you always sprint when moving forward. */
public class ToggleSprintModule extends Module {
    public ToggleSprintModule() { super("Toggle Sprint", "Always sprint while moving forward", Category.COMBAT, true); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.options.keySprint.setDown(true);
    }

    @Override
    protected void onDisable() {
        mc.options.keySprint.setDown(false);
    }
}
