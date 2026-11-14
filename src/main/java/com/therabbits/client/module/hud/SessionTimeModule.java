package com.therabbits.client.module.hud;

/** How long you have been in the current world or server. */
public class SessionTimeModule extends TextHudModule {
    private Object level;
    private long start = System.currentTimeMillis();

    public SessionTimeModule() { super("Session Time", "Time spent in this world or server", false, 300, 94); }

    @Override
    public void onTick() {
        if (mc.level != level) { level = mc.level; start = System.currentTimeMillis(); }
    }

    @Override
    protected String text(boolean editing) {
        long s = (System.currentTimeMillis() - start) / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }
}
