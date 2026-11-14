package com.therabbits.client.module.hud;

/** Horizontal speed in blocks per second. */
public class SpeedModule extends TextHudModule {
    private double lastX, lastZ, speed;
    private boolean has;

    public SpeedModule() { super("Speed", "Blocks per second", false, 4, 304); }

    @Override
    public void onTick() {
        if (mc.player == null) { has = false; return; }
        double x = mc.player.getX(), z = mc.player.getZ();
        if (has) {
            double now = Math.hypot(x - lastX, z - lastZ) * 20;
            speed = speed * 0.6 + now * 0.4;
        }
        lastX = x; lastZ = z; has = true;
    }

    @Override protected String text(boolean editing) { return String.format("%.2f m/s", speed); }
}
