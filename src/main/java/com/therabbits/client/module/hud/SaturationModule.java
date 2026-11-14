package com.therabbits.client.module.hud;

public class SaturationModule extends TextHudModule {
    public SaturationModule() { super("Saturation", "Hidden food saturation", false, 4, 232); }

    @Override
    protected String text(boolean editing) {
        if (mc.player == null) return "Saturation 5.0";
        return String.format("Saturation %.1f", mc.player.getFoodData().getSaturationLevel());
    }
}
