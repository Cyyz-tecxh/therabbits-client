package com.therabbits.client.module.cosmetics;

import com.therabbits.client.account.Account;
import com.therabbits.client.cosmetics.Capes;
import com.therabbits.client.cosmetics.CosmeticsSync;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.ModeSetting;
import net.minecraft.core.ClientAsset;

/** TheRabbits capes. Sharing with other players is set up in Account & Codes (for all cosmetics). */
public class CapesModule extends Module {
    private static CapesModule instance;

    public final ModeSetting cape = add(new ModeSetting("Cape", 1, false, Capes.NAMES)
            .labels(i -> Capes.NAMES[i] + (Account.isLocked(Capes.IDS[i]) ? " (locked)" : "")));

    public CapesModule() {
        super("Capes", "Animated TheRabbits capes", Category.COSMETICS, true);
        instance = this;
    }

    public static CapesModule get() { return instance; }

    public String capeId() {
        String id = Capes.IDS[cape.get()];
        return Account.isLocked(id) ? "none" : id;
    }

    /** Your own cape texture (shown right away, even before sharing). */
    public static ClientAsset.Texture ownCape() {
        return instance != null && instance.enabled() ? Capes.texture(instance.capeId()) : null;
    }

    public static ClientAsset.Texture capeFor(java.util.UUID id) {
        if (instance == null || !instance.enabled() || !AccountModule.showOthersOn()) return null;
        return Capes.texture(CosmeticsSync.capeOf(id));
    }
}
