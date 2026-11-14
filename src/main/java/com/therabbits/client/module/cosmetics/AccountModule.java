package com.therabbits.client.module.cosmetics;

import com.therabbits.client.account.Account;
import com.therabbits.client.cosmetics.CosmeticsSync;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.ActionSetting;
import com.therabbits.client.setting.InfoSetting;
import com.therabbits.client.setting.TextSetting;

import java.util.concurrent.CompletableFuture;

/**
 * Everything online: the TheRabbits server, sharing ALL your cosmetics (capes, hats, arms, shields, auras)
 * with other TheRabbits players, codes, friends and parties.
 */
public class AccountModule extends Module {
    private static AccountModule instance;
    public final TextSetting serverUrl = new TextSetting("Server URL", "", 120);
    public final BoolSetting showOthers = new BoolSetting("Show Others' Cosmetics", true);
    private ActionSetting share;
    private String lastShared = "", lastUrl = "";
    public final TextSetting code = new TextSetting("Code", "", 40);
    public final TextSetting friendCode = new TextSetting("Friend's Code", "", 12);
    public final TextSetting partyCode = new TextSetting("Party Code", "", 12);
    private String result = "";

    public AccountModule() {
        super("Account & Codes", "Server, sharing, codes", Category.COSMETICS, true);
        instance = this;
        add(serverUrl);
        add(showOthers);
        share = add(new ActionSetting("Share My Cosmetics", "Share Now", this::shareNow));
        add(new InfoSetting("Status", () -> result.isEmpty() ? Account.status : result));
        ActionSetting connect = add(new ActionSetting("Connect Account", "Connect", () -> {}));
        connect.setAction(() -> run(connect, "Connecting...", "Connect", Account.login()));
        add(code);
        ActionSetting redeem = add(new ActionSetting("Redeem Code", "Redeem", () -> {}));
        redeem.setAction(() -> {
            if (code.get().isBlank()) { result = "Type a code first"; return; }
            run(redeem, "Redeeming...", "Redeem", Account.redeem(code.get()).thenApply(s -> { code.set(""); return s; }));
        });
        add(new InfoSetting("Unlocked Cosmetics", () -> Account.owned.isEmpty() ? "None" : Account.owned.size() + " unlocked"));
        add(new InfoSetting("Supporting Creator", () -> Account.creator == null ? "None" : Account.creator));
        add(new InfoSetting("Your Friend Code", () -> Account.friendCode == null ? "Connect to get one" : Account.friendCode));
        add(friendCode);
        ActionSetting addFriend = add(new ActionSetting("Add Friend", "Add", () -> {}));
        addFriend.setAction(() -> {
            if (friendCode.get().isBlank()) { result = "Type your friend's code first"; return; }
            run(addFriend, "Adding...", "Add", Account.addFriend(friendCode.get()).thenApply(s -> { friendCode.set(""); return s; }));
        });
        add(new InfoSetting("Friends", Account::friendsLine));
        ActionSetting create = add(new ActionSetting("Create Party", "Create", () -> {}));
        create.setAction(() -> run(create, "Creating...", "Create", Account.createParty()));
        add(partyCode);
        ActionSetting join = add(new ActionSetting("Join Party", "Join", () -> {}));
        join.setAction(() -> {
            if (partyCode.get().isBlank()) { result = "Type a party code first"; return; }
            run(join, "Joining...", "Join", Account.joinParty(partyCode.get()).thenApply(s -> { partyCode.set(""); return s; }));
        });
        ActionSetting leave = add(new ActionSetting("Leave Party", "Leave", () -> {}));
        leave.setAction(() -> run(leave, "Leaving...", "Leave", Account.leaveParty()));
        add(new InfoSetting("Party", Account::partyLine));
        ActionSetting refresh = add(new ActionSetting("Refresh", "Refresh", () -> { Account.refreshNow(); result = ""; }));
    }

    private void run(ActionSetting button, String busy, String label, CompletableFuture<String> job) {
        button.set(busy);
        job.thenAccept(msg -> { result = msg; button.set(label); });
    }

    public static AccountModule get() { return instance; }

    public static boolean showOthersOn() { return instance == null || (instance.enabled() && instance.showOthers.on()); }

    public static String url() { return instance == null ? "" : instance.serverUrl.get().trim(); }

    /** Sends everything you're wearing (cape, hat, arms, shield, aura) to the server. */
    private void shareNow() {
        share.set("Sharing...");
        CapesModule c = CapesModule.get();
        WearablesModule w = WearablesModule.get();
        String cape = c != null && c.enabled() ? c.capeId() : "none";
        CosmeticsSync.publish(url(), cape, w != null ? w.hatId() : "none", w != null ? w.armsId() : "none",
                w != null ? w.shieldId() : "none", w != null ? w.auraId() : "none").thenAccept(msg -> { share.set("Share Now"); result = msg; });
        lastShared = signature();
        lastUrl = url();
    }

    /** What we'd share right now; any change is shared automatically. */
    private String signature() {
        CapesModule c = CapesModule.get();
        WearablesModule w = WearablesModule.get();
        return (c != null && c.enabled() ? c.capeId() : "none") + "|" + (w != null ? w.hatId() + "|" + w.armsId() + "|" + w.shieldId() + "|" + w.auraId() : "");
    }

    @Override
    public void onTick() {
        Account.tick();
        CosmeticsSync.tick(url());
        // auto-share when you change any cosmetic (once you're in a world, so the login check can run)
        if (mc.player != null && !url().isBlank() && (!signature().equals(lastShared) || !url().equals(lastUrl))) shareNow();
    }
}
