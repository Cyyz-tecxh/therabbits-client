package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;

/** Shows entity hitboxes (same as F3+B) while enabled. */
public class HitboxesModule extends Module {
    public HitboxesModule() { super("Hitboxes", "Show entity hitboxes (like F3+B)", Category.COMBAT, false); }

    @Override
    public void onTick() { sync(true); }

    @Override
    protected void onDisable() { sync(false); }

    private void sync(boolean want) {
        if (mc.debugEntries == null) return;
        if (mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES) != want)
            mc.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
    }
}
