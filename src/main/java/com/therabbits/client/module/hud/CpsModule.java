package com.therabbits.client.module.hud;

import com.therabbits.client.hud.CpsTracker;

public class CpsModule extends TextHudModule {
    public CpsModule() { super("CPS", "Clicks per second", false, 4, 340); }

    @Override protected String text(boolean editing) { return CpsTracker.left() + " | " + CpsTracker.right() + " CPS"; }
}
