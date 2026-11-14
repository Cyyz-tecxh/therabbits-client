package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Entity hitboxes with filters. Turns F3+B on when enabled and off when disabled, but otherwise
 * leaves it alone, so pressing F3+B yourself still works.
 */
public class HitboxesModule extends Module {
    private static HitboxesModule instance;
    public final BoolSetting players = add(new BoolSetting("Players", true));
    public final BoolSetting self = add(new BoolSetting("Your Own Hitbox", false));
    public final BoolSetting mobs = add(new BoolSetting("Mobs & Animals", true));
    public final BoolSetting items = add(new BoolSetting("Dropped Items", false));
    public final BoolSetting projectiles = add(new BoolSetting("Arrows & Projectiles", true));
    public final BoolSetting other = add(new BoolSetting("Everything Else", false));

    public HitboxesModule() { super("Hitboxes", "Show entity hitboxes", Category.COMBAT, false); instance = this; }

    public static HitboxesModule get() { return instance; }

    private boolean applied;

    @Override
    protected void onEnable() { applied = false; }

    @Override
    public void onTick() { if (!applied && mc.debugEntries != null) { set(true); applied = true; } }

    @Override
    protected void onDisable() { set(false); applied = false; }

    private void set(boolean want) {
        if (mc.debugEntries == null) return;
        if (mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES) != want)
            mc.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
    }

    /** Whether this entity's hitbox should be drawn (only filters while the module is on). */
    public boolean shows(Entity e) {
        if (!enabled()) return true;
        if (e == mc.player || e == mc.getCameraEntity()) return self.on() && !mc.options.getCameraType().isFirstPerson();
        if (e instanceof Player) return players.on();
        if (e instanceof LivingEntity) return mobs.on();
        if (e instanceof ItemEntity) return items.on();
        if (e instanceof Projectile) return projectiles.on();
        return other.on();
    }
}
