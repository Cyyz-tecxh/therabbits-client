package com.therabbits.client.module.cosmetics;

import com.therabbits.client.cosmetics.Capes;
import com.therabbits.client.cosmetics.CosmeticsSync;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.ActionSetting;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;
import com.therabbits.client.setting.TextSetting;
import net.minecraft.core.ClientAsset;

/** TheRabbits capes: wear one, see other TheRabbits players' capes. */
public class CapesModule extends Module {
    private static CapesModule instance;

    public final ModeSetting cape = add(new ModeSetting("Cape", 1, false, Capes.NAMES)
            .labels(i -> Capes.NAMES[i] + (com.therabbits.client.account.Account.isLocked(Capes.IDS[i]) ? " (locked)" : "")));
    public final BoolSetting showOthers = add(new BoolSetting("Show Others' Capes", true));
    public final TextSetting serverUrl = add(new TextSetting("Server URL", "", 120));
    private final ActionSetting sync;
    private String lastSynced = "";
    private String lastUrl = "";

    public CapesModule() {
        super("Capes", "Animated TheRabbits capes", Category.COSMETICS, true);
        instance = this;
        sync = add(new ActionSetting("Share With Others", "Sync Now", this::syncNow));
    }

    public static CapesModule get() { return instance; }

    public String capeId() {
        String id = Capes.IDS[cape.get()];
        return com.therabbits.client.account.Account.isLocked(id) ? "none" : id;
    }

    /** Your own cape texture (shown right away, even before syncing). */
    public static ClientAsset.Texture ownCape() {
        return instance != null && instance.enabled() ? Capes.texture(instance.capeId()) : null;
    }

    public static ClientAsset.Texture capeFor(java.util.UUID id) {
        if (instance == null || !instance.enabled() || !instance.showOthers.on()) return null;
        return Capes.texture(CosmeticsSync.capeOf(id));
    }

    private void syncNow() {
        sync.set("Syncing...");
        WearablesModule w = WearablesModule.get();
        String hat = w != null ? w.hatId() : "none", arms = w != null ? w.armsId() : "none", sh = w != null ? w.shieldId() : "none", au = w != null ? w.auraId() : "none";
        CosmeticsSync.publish(serverUrl.get(), enabled() ? capeId() : "none", hat, arms, sh, au).thenAccept(sync::set);
        lastSynced = signature();
        lastUrl = serverUrl.get();
    }

    /** What we'd publish right now; a change triggers an automatic re-sync. */
    private String signature() {
        WearablesModule w = WearablesModule.get();
        return (enabled() ? capeId() : "none") + "|" + (w != null ? w.hatId() + "|" + w.armsId() + "|" + w.shieldId() + "|" + w.auraId() : "");
    }

    @Override
    public void onTick() {
        CosmeticsSync.tick(serverUrl.get());
        // auto-share when you pick a new cape (once you're in a world, so the login check can run)
        if (mc.player != null && !serverUrl.get().isBlank() && (!signature().equals(lastSynced) || !serverUrl.get().equals(lastUrl))) syncNow();
    }
}
