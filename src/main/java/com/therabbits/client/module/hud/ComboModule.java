package com.therabbits.client.module.hud;

/** Hits in a row without getting hit back (resets after 3 s or when you take damage). */
public class ComboModule extends TextHudModule {
    private static int combo;
    private static long last;

    public ComboModule() { super("Combo Counter", "Hits in a row without being hit", true, 4, 322); }

    public static void onHit() { combo++; last = System.currentTimeMillis(); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.hurtTime > 0 || System.currentTimeMillis() - last > 3000) combo = 0;
    }

    @Override protected String text(boolean editing) { return (editing && combo == 0 ? 3 : combo) + " Combo"; }
}
