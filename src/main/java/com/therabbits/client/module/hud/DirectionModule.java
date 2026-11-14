package com.therabbits.client.module.hud;

import net.minecraft.util.Mth;

public class DirectionModule extends TextHudModule {
    private static final String[] NAMES = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    public DirectionModule() { super("Direction", "Facing direction", false, 4, 58); }

    @Override
    protected String text(boolean editing) {
        if (mc.player == null) return "N  180";
        float yaw = Mth.wrapDegrees(mc.player.getYRot());
        int idx = Math.floorMod(Math.round((yaw < 0 ? yaw + 360 : yaw) / 45f), 8);
        return NAMES[idx] + "  " + Math.round(yaw);
    }
}
