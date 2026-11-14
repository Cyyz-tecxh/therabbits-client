package com.therabbits.client.module.utility;

import com.mojang.blaze3d.platform.InputConstants;
import com.therabbits.client.RabbitsClient;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.KeySetting;
import com.therabbits.client.setting.TextSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** Press a key to send a message you set (start with / for commands). */
public class QuickMessagesModule extends Module {
    private static final int SLOTS = 6;
    private final KeyMapping[] keys = new KeyMapping[SLOTS];
    private final TextSetting[] messages = new TextSetting[SLOTS];
    private long lastSent;

    public QuickMessagesModule() {
        super("Quick Messages", "Press a key to send a message or command", Category.UTILITY, true);
        for (int i = 0; i < SLOTS; i++) {
            keys[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.therabbits.quick" + (i + 1),
                    InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), RabbitsClient.CATEGORY));
            messages[i] = add(new TextSetting("Message " + (i + 1), i == 0 ? "gg" : "", 256));
            add(new KeySetting("Key " + (i + 1), keys[i]));
        }
    }

    @Override
    public void onTick() {
        for (int i = 0; i < SLOTS; i++) {
            while (keys[i].consumeClick()) {
                String msg = messages[i].get().trim();
                if (msg.isEmpty() || mc.player == null || mc.screen != null) continue;
                long now = System.currentTimeMillis();
                if (now - lastSent < 400) continue;   // don't spam if a key is held
                lastSent = now;
                if (msg.startsWith("/")) mc.player.connection.sendCommand(msg.substring(1));
                else mc.player.connection.sendChat(msg);
            }
        }
    }
}
