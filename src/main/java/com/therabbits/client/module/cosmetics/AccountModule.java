package com.therabbits.client.module.cosmetics;

import com.therabbits.client.account.Account;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.ActionSetting;
import com.therabbits.client.setting.InfoSetting;
import com.therabbits.client.setting.TextSetting;

import java.util.concurrent.CompletableFuture;

/** Codes, friends and parties (needs the TheRabbits server URL, set under Capes). */
public class AccountModule extends Module {
    public final TextSetting code = new TextSetting("Code", "", 40);
    public final TextSetting friendCode = new TextSetting("Friend's Code", "", 12);
    public final TextSetting partyCode = new TextSetting("Party Code", "", 12);
    private String result = "";

    public AccountModule() {
        super("Account & Codes", "Codes, friends, parties", Category.COSMETICS, true);
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

    @Override
    public void onTick() { Account.tick(); }
}
