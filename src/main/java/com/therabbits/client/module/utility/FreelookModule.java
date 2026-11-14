package com.therabbits.client.module.utility;

import com.therabbits.client.RabbitsClient;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

/** Hold Left Alt to look around your player without turning. */
public class FreelookModule extends Module {
    private static FreelookModule instance;
    private final BoolSetting thirdPerson = add(new BoolSetting("Third Person", true));
    private boolean active;
    private float yaw, pitch;
    private CameraType previous;

    public FreelookModule() {
        super("Freelook", "Hold Alt to look around", Category.UTILITY, true);
        instance = this;
    }

    public static FreelookModule get() { return instance; }
    public boolean active() { return active; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    @Override
    public void onTick() {
        boolean down = RabbitsClient.freelookKey != null && RabbitsClient.freelookKey.isDown() && mc.player != null && mc.screen == null;
        if (down && !active) {
            active = true;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            previous = mc.options.getCameraType();
            if (thirdPerson.on()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!down && active) {
            stop();
        }
    }

    @Override
    protected void onDisable() { if (active) stop(); }

    private void stop() {
        active = false;
        if (previous != null) mc.options.setCameraType(previous);
    }

    /** Called from EntityMixin instead of turning the player. */
    public void turn(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }
}
