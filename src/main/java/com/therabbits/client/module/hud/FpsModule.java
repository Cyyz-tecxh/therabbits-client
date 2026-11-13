package com.therabbits.client.module.hud;

public class FpsModule extends TextHudModule {
    public FpsModule() { super("FPS", "Shows your frames per second", true, 4, 4); }

    @Override protected String text(boolean editing) { return mc.getFps() + " FPS"; }
}
