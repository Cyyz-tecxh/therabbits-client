package com.therabbits.client.module.hud;

public class CoordinatesModule extends TextHudModule {
    public CoordinatesModule() { super("Coordinates", "X Y Z position", false, 4, 40); }

    @Override
    protected String text(boolean editing) {
        if (mc.player == null) return "X 0  Y 64  Z 0";
        return "X " + mc.player.getBlockX() + "  Y " + mc.player.getBlockY() + "  Z " + mc.player.getBlockZ();
    }
}
