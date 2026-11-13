package com.therabbits.client.module.combat;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/** Replaces the vanilla crosshair (drawn from GuiMixin). */
public class CrosshairModule extends Module {
    private final NumberSetting style = add(new NumberSetting("Style (0 Cross, 1 Dot, 2 Box)", 0, 0, 2, 1));
    private final NumberSetting size = add(new NumberSetting("Size", 5, 1, 12, 1));
    private final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 8, 1));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 1, 4, 1));
    private final BoolSetting dot = add(new BoolSetting("Center Dot", false));
    private final BoolSetting outline = add(new BoolSetting("Outline", true));

    public CrosshairModule() { super("Crosshair", "Custom black & white crosshair", Category.COMBAT, true); }

    public void draw(GuiGraphics g) {
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int s = size.i(), gp = gap.i(), t = thickness.i(), h = t / 2;
        switch (style.i()) {
            case 1 -> bar(g, cx - h - 1, cy - h - 1, t + 2, t + 2);
            case 2 -> {
                int r = s + gp;
                bar(g, cx - r, cy - r, r * 2 + t, t);
                bar(g, cx - r, cy + r, r * 2 + t, t);
                bar(g, cx - r, cy - r, t, r * 2 + t);
                bar(g, cx + r, cy - r, t, r * 2 + t);
            }
            default -> {
                bar(g, cx - h, cy - gp - s - h, t, s);          // up
                bar(g, cx - h, cy + gp + 1 - h + (t > 1 ? 1 : 0), t, s); // down
                bar(g, cx - gp - s - h, cy - h, s, t);          // left
                bar(g, cx + gp + 1 - h + (t > 1 ? 1 : 0), cy - h, s, t); // right
            }
        }
        if (dot.on() && style.i() != 1) bar(g, cx - h, cy - h, t, t);
    }

    private void bar(GuiGraphics g, int x, int y, int w, int hgt) {
        if (outline.on()) g.fill(x - 1, y - 1, x + w + 1, y + hgt + 1, 0xC0000000);
        g.fill(x, y, x + w, y + hgt, 0xFFFFFFFF);
    }
}
