package com.therabbits.client.module.combat;

import com.therabbits.client.RabbitsClient;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.NumberSetting;

/** Hold the zoom key (C by default) to zoom in. Used by GameRendererMixin. */
public class ZoomModule extends Module {
    private final NumberSetting amount = add(new NumberSetting("Zoom Amount", 4, 2, 10, 0.5));
    private final BoolSetting smooth = add(new BoolSetting("Smooth", true));
    private double current = 1;

    public ZoomModule() { super("Zoom", "Hold C to zoom", Category.COMBAT, true); }

    /** Divides the field of view; 1 = no zoom. Called every frame. */
    public double factor() {
        double target = enabled() && RabbitsClient.zoomKey != null && RabbitsClient.zoomKey.isDown() && mc.screen == null
                ? amount.get() : 1;
        current = smooth.on() ? current + (target - current) * 0.25 : target;
        if (Math.abs(current - target) < 0.01) current = target;
        return current;
    }
}
