package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;
import com.therabbits.client.setting.NumberSetting;

/** Client-side time of day and weather (only you see it; the server is unchanged). */
public class TimeWeatherModule extends Module {
    private static TimeWeatherModule instance;
    public final BoolSetting changeTime = add(new BoolSetting("Change Time", true));
    public final NumberSetting time = add(new NumberSetting("Time (0 sunrise, 6000 noon, 18000 midnight)", 6000, 0, 23999, 250));
    public final BoolSetting cycle = add(new BoolSetting("Let Time Move", false));
    public final ModeSetting weather = add(new ModeSetting("Weather", 1, false, "Server", "Clear", "Rain", "Thunder"));

    public TimeWeatherModule() { super("Time & Weather", "Set your own time of day and weather", Category.VISUAL, false); instance = this; }

    public static TimeWeatherModule get() { return instance; }

    private long start = System.currentTimeMillis();

    @Override
    protected void onEnable() { start = System.currentTimeMillis(); }

    /** Day time to show, or -1 to keep the server's. */
    public long dayTime(long server) {
        if (!enabled() || !changeTime.on()) return -1;
        long base = (long) time.get().doubleValue();
        if (cycle.on()) base += (System.currentTimeMillis() - start) / 50;   // 20 ticks per second
        return (server / 24000L) * 24000L + (base % 24000L);
    }

    /** Rain level 0..1, or -1 to keep the server's. */
    public float rain() {
        if (!enabled()) return -1;
        return switch (weather.get()) { case 1 -> 0; case 2, 3 -> 1; default -> -1; };
    }

    public float thunder() {
        if (!enabled()) return -1;
        return switch (weather.get()) { case 1, 2 -> 0; case 3 -> 1; default -> -1; };
    }
}
