package com.therabbits.client.module.cosmetics;

import com.therabbits.client.cosmetics.CosmeticsSync;
import com.therabbits.client.cosmetics.Wearables;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** 3D hats and arm pieces. Shared with other TheRabbits players through the Capes module's server. */
public class WearablesModule extends Module {
    private static WearablesModule instance;

    public final ModeSetting hat = add(new ModeSetting("Hat", 2, false, Wearables.HAT_NAMES));
    public final ModeSetting arms = add(new ModeSetting("Arms", 0, false, Wearables.ARM_NAMES));
    public final ModeSetting shield = add(new ModeSetting("Shield", 0, false, Wearables.SHIELD_NAMES));
    public final BoolSetting showOthers = add(new BoolSetting("Show Others' Cosmetics", true));

    public WearablesModule() {
        super("Hats & Arms", "3D hats, claws and shield skins that other TheRabbits players see", Category.COSMETICS, true);
        instance = this;
    }

    public static WearablesModule get() { return instance; }

    public String hatId() { return enabled() ? Wearables.HAT_IDS[hat.get()] : "none"; }
    public String shieldId() { return enabled() ? Wearables.SHIELD_IDS[shield.get()] : "none"; }
    public String armsId() { return enabled() ? Wearables.ARM_IDS[arms.get()] : "none"; }

    private static boolean isMe(Player p) {
        Minecraft mc = Minecraft.getInstance();
        return p == mc.player || (mc.getUser() != null && p.getUUID().equals(mc.getUser().getProfileId()));
    }

    public static String hatFor(Player p) {
        if (instance == null) return null;
        if (isMe(p)) return instance.hatId();
        return instance.enabled() && instance.showOthers.on() ? CosmeticsSync.hatOf(p.getUUID()) : null;
    }

    public static String shieldFor(Player p) {
        if (instance == null) return null;
        if (isMe(p)) return instance.shieldId();
        return instance.enabled() && instance.showOthers.on() ? CosmeticsSync.shieldOf(p.getUUID()) : null;
    }

    public static String ownShield() { return instance == null ? null : instance.shieldId(); }

    public static String armsFor(Player p) {
        if (instance == null) return null;
        if (isMe(p)) return instance.armsId();
        return instance.enabled() && instance.showOthers.on() ? CosmeticsSync.armsOf(p.getUUID()) : null;
    }
}
