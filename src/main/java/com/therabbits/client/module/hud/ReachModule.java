package com.therabbits.client.module.hud;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shows how far away you were when you last hit something. */
public class ReachModule extends TextHudModule {
    private static double last = -1;
    private static long lastTime;

    public ReachModule() { super("Reach Display", "Last hit distance", true, 4, 268); }

    public static void onAttack(Entity attacker, Entity target) {
        Vec3 eye = attacker.getEyePosition();
        AABB box = target.getBoundingBox();
        double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
        double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        last = eye.distanceTo(new Vec3(x, y, z));
        lastTime = System.currentTimeMillis();
    }

    @Override
    protected String text(boolean editing) {
        if (editing && last < 0) return "3.00 blocks";
        if (last < 0 || System.currentTimeMillis() - lastTime > 3000) return "Reach --";
        return String.format("%.2f blocks", last);
    }
}
