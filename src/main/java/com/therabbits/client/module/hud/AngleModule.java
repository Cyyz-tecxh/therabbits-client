package com.therabbits.client.module.hud;

import net.minecraft.util.Mth;

public class AngleModule extends TextHudModule {
    public AngleModule() { super("Look Angle", "Shows your yaw and pitch", false, 300, 76); }

    @Override
    protected String text(boolean editing) {
        if (mc.player == null) return "Yaw 0.0  Pitch 0.0";
        return String.format("Yaw %.1f  Pitch %.1f", Mth.wrapDegrees(mc.player.getYRot()), mc.player.getXRot());
    }
}
