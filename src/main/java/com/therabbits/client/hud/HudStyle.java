package com.therabbits.client.hud;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.gui.Draw;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.module.hud.HudStyleModule;
import net.minecraft.client.gui.GuiGraphics;

/** Colors and text drawing for HUD elements, following HUD Style plus the element's own override. */
public final class HudStyle {
    private HudStyle() {}

    static HudModule current;

    private static HudStyleModule s() { return ModuleManager.get(HudStyleModule.class); }

    public static float size() { return s().enabled() ? s().textSize.f() : 1f; }

    /** Main text color (ARGB). */
    public static int text() { return text(0); }

    /** Main text color; offset only matters for Chroma (staggers the rainbow per line). */
    public static int text(float offset) {
        if (current != null && !current.textColor.global()) return 0xFF000000 | current.textColor.color(offset);
        return 0xFF000000 | (s().enabled() ? s().textColor.color(offset) : 0xFFFFFF);
    }

    /** Secondary text color for labels like "Totem Pops" or timers. */
    public static int dim() {
        if (current != null && !current.labelColor.global()) return 0xFF000000 | current.labelColor.color(0);
        return 0xFF000000 | (s().enabled() ? s().labelColor.color(0) : 0x909090);
    }

    public static int bg() {
        int a = s().enabled() ? (int) Math.round(s().bgOpacity.get() * 2.55) : 144;
        return a << 24;
    }

    public static boolean shadow() { return !s().enabled() || s().shadow.on(); }

    public static void text(GuiGraphics g, String str, int x, int y, int color) {
        if (shadow()) Draw.textShadow(g, str, x, y, color); else Draw.text(g, str, x, y, color);
    }
}
