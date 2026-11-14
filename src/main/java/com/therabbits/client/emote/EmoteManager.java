package com.therabbits.client.emote;

import com.therabbits.client.emote.Emotes.Emote;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** Plays emotes for you: switches to front view while it plays, stops when you move. */
public final class EmoteManager {
    private EmoteManager() {}

    private static Emote current;
    private static long start;
    private static CameraType before;
    private static int graceTicks;

    public static Emote current() { return current; }

    public static boolean active() { return current != null; }

    /** Seconds since the emote started. */
    public static float time() { return (System.currentTimeMillis() - start) / 1000f; }

    /** 0..1 through the emote (loops wrap around). */
    public static float progress() {
        if (current == null) return 0;
        float t = time() / current.seconds();
        return current.loops() ? t % 1f : Math.min(1f, t);
    }

    public static void play(Emote e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (current == null) before = mc.options.getCameraType();
        current = e;
        start = System.currentTimeMillis();
        graceTicks = 6;
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }

    public static void stop() {
        if (current == null) return;
        current = null;
        Minecraft mc = Minecraft.getInstance();
        if (before != null) mc.options.setCameraType(before);
        before = null;
    }

    public static void tick() {
        if (current == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { current = null; return; }
        if (graceTicks > 0) { graceTicks--; return; }
        var o = mc.options;
        boolean moved = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()
                || o.keyJump.isDown() || o.keyAttack.isDown() || o.keyUse.isDown();
        boolean done = !current.loops() && time() >= current.seconds();
        boolean tooLong = current.loops() && time() >= current.seconds() * 3;
        if (moved || done || tooLong) stop();
    }
}
