package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.hud.HudStyle;
import com.therabbits.client.module.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Counts totem pops per player (reset when you change world). */
public class TotemPopModule extends HudModule {
    private static final Map<String, Integer> POPS = new LinkedHashMap<>();
    private static Object level;

    public TotemPopModule() { super("Totem Pops", "Count totem pops", true, 4, 286); }

    public static void onPop(Entity e) {
        String name = e.getName().getString();
        POPS.merge(name, 1, Integer::sum);
        // keep most recent at the end
        int n = POPS.remove(name);
        POPS.put(name, n);
        while (POPS.size() > 4) POPS.remove(POPS.keySet().iterator().next());
    }

    @Override
    public void onTick() {
        if (mc.level != level) { level = mc.level; POPS.clear(); }
    }

    private Map<String, Integer> shown(boolean editing) {
        if (POPS.isEmpty() && editing) return Map.of("Player", 2);
        return POPS;
    }

    @Override public int width() { return 110; }
    @Override public int height() { return 14 + Math.max(1, shown(true).size()) * 10; }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        Map<String, Integer> m = shown(editing);
        if (m.isEmpty() && !editing) return;
        Draw.rect(g, 0, 0, width(), height(), HudStyle.bg());
        HudStyle.text(g, "Totem Pops", 4, 3, HudStyle.dim());
        int y = 14;
        for (var e : m.entrySet()) {
            HudStyle.text(g, e.getKey(), 4, y, HudStyle.text(y / 60f));
            String c = "x" + e.getValue();
            HudStyle.text(g, c, width() - 4 - Draw.width(c), y, HudStyle.text(y / 60f));
            y += 10;
        }
    }
}
