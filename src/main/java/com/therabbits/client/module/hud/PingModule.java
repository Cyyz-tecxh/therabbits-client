package com.therabbits.client.module.hud;

import net.minecraft.client.multiplayer.PlayerInfo;

public class PingModule extends TextHudModule {
    public PingModule() { super("Ping", "Connection latency", true, 4, 22); }

    @Override
    protected String text(boolean editing) {
        if (mc.player == null || mc.getConnection() == null) return "0 ms";
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return (info == null ? 0 : info.getLatency()) + " ms";
    }
}
