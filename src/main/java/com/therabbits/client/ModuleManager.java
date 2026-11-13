package com.therabbits.client;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.module.Module;
import com.therabbits.client.module.combat.ToggleSprintModule;
import com.therabbits.client.module.combat.ZoomModule;
import com.therabbits.client.module.hud.*;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();

    private ModuleManager() {}

    static void init() {
        // HUD
        add(new FpsModule());
        add(new PingModule());
        add(new CoordinatesModule());
        add(new DirectionModule());
        add(new KeystrokesModule());
        add(new ArmorHudModule());
        // PvP
        add(new ToggleSprintModule());
        add(new ZoomModule());
    }

    private static void add(Module m) { MODULES.add(m); }

    public static List<Module> all() { return MODULES; }

    public static List<Module> in(Category c) {
        return MODULES.stream().filter(m -> m.category() == c).toList();
    }

    public static List<HudModule> hud() {
        List<HudModule> out = new ArrayList<>();
        for (Module m : MODULES) if (m instanceof HudModule h) out.add(h);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> type) {
        for (Module m : MODULES) if (type.isInstance(m)) return (T) m;
        throw new IllegalArgumentException(type.getName());
    }
}
