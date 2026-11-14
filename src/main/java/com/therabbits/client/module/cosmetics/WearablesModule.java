package com.therabbits.client.module.cosmetics;

import com.therabbits.client.account.Account;
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

    public final ModeSetting hat = add(new ModeSetting("Hat", 2, false, Wearables.HAT_NAMES).labels(i -> lockLabel(Wearables.HAT_NAMES[i], Wearables.HAT_IDS[i])));
    public final ModeSetting arms = add(new ModeSetting("Arms", 0, false, Wearables.ARM_NAMES).labels(i -> lockLabel(Wearables.ARM_NAMES[i], Wearables.ARM_IDS[i])));
    public final ModeSetting shield = add(new ModeSetting("Shield", 0, false, Wearables.SHIELD_NAMES).labels(i -> lockLabel(Wearables.SHIELD_NAMES[i], Wearables.SHIELD_IDS[i])));

    private static String lockLabel(String name, String id) { return name + (Account.isLocked(id) ? " (locked)" : ""); }
    private static String unlocked(String id) { return Account.isLocked(id) ? "none" : id; }
    public final ModeSetting aura = add(new ModeSetting("Aura", 0, false, Wearables.AURA_NAMES).labels(i -> lockLabel(Wearables.AURA_NAMES[i], Wearables.AURA_IDS[i])));
    public final BoolSetting showOthers = add(new BoolSetting("Show Others' Cosmetics", true));

    public WearablesModule() {
        super("Hats & Arms", "Hats, claws, shields, auras", Category.COSMETICS, true);
        instance = this;
    }

    public static WearablesModule get() { return instance; }

    public String hatId() { return enabled() ? unlocked(Wearables.HAT_IDS[hat.get()]) : "none"; }
    public String auraId() { return enabled() ? unlocked(Wearables.AURA_IDS[aura.get()]) : "none"; }
    public String shieldId() { return enabled() ? unlocked(Wearables.SHIELD_IDS[shield.get()]) : "none"; }
    public String armsId() { return enabled() ? unlocked(Wearables.ARM_IDS[arms.get()]) : "none"; }

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

    public static String auraFor(Player p) {
        if (instance == null) return null;
        if (isMe(p)) return instance.auraId();
        return instance.enabled() && instance.showOthers.on() ? CosmeticsSync.auraOf(p.getUUID()) : null;
    }

    public static String ownShield() { return instance == null ? null : instance.shieldId(); }

    public static String armsFor(Player p) {
        if (instance == null) return null;
        if (isMe(p)) return instance.armsId();
        return instance.enabled() && instance.showOthers.on() ? CosmeticsSync.armsOf(p.getUUID()) : null;
    }
}
