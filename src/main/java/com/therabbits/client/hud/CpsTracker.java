package com.therabbits.client.hud;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/** Counts left/right clicks over the last second by watching the attack/use keys each frame. */
public final class CpsTracker {
    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static boolean leftWasDown, rightWasDown;

    private CpsTracker() {}

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        boolean l = mc.options.keyAttack.isDown();
        boolean r = mc.options.keyUse.isDown();
        if (l && !leftWasDown) LEFT.add(now);
        if (r && !rightWasDown) RIGHT.add(now);
        leftWasDown = l;
        rightWasDown = r;
        while (!LEFT.isEmpty() && now - LEFT.peekFirst() > 1000) LEFT.pollFirst();
        while (!RIGHT.isEmpty() && now - RIGHT.peekFirst() > 1000) RIGHT.pollFirst();
    }

    public static int left() { return LEFT.size(); }
    public static int right() { return RIGHT.size(); }
}
