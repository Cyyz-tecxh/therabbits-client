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
        int override = current == null ? 0 : current.textColor.get();
        int idx = override > 0 ? override - 1 : (s().enabled() ? s().textColor.get() : 0);
        return 0xFF000000 | HudColors.rgb(idx, offset);
    }

    /** Secondary text color for labels like "Totem Pops" or timers. */
    public static int dim() {
        return 0xFF000000 | HudColors.rgb(s().enabled() ? s().labelColor.get() : 2, 0);
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
