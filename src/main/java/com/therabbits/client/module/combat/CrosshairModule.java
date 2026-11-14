package com.therabbits.client.module.combat;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;
import com.therabbits.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.HitResult;

/** Custom crosshair (aim): style, size, gap, thickness, colour, outline, target colour. Drawn from GuiMixin. */
public class CrosshairModule extends Module {
    public final ModeSetting style = add(new ModeSetting("Style", 0, false, "Cross", "Dot", "Box", "Circle", "T-Shape", "X", "Plus + Dot"));
    private final NumberSetting size = add(new NumberSetting("Size", 5, 1, 14, 1));
    private final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 10, 1));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 1, 4, 1));
    private final ModeSetting color = add(new ModeSetting("Colour", 0, false, "White", "Black", "Red", "Green", "Cyan", "Yellow", "Pink", "Purple", "Orange"));
    private final NumberSetting opacity = add(new NumberSetting("Opacity", 1, 0.2, 1, 0.05));
    private final BoolSetting dot = add(new BoolSetting("Center Dot", false));
    private final BoolSetting outline = add(new BoolSetting("Outline", true));
    private final BoolSetting targetColor = add(new BoolSetting("Red When Aiming At A Player/Mob", true));
    private final BoolSetting thirdPerson = add(new BoolSetting("Show In Third Person", false));

    private static final int[] COLORS = {0xFFFFFF, 0x000000, 0xFF3B3B, 0x4CFF6A, 0x3BE6FF, 0xFFE23B, 0xFF6EC7, 0xB46BFF, 0xFF9A2E};

    public CrosshairModule() { super("Crosshair", "Custom crosshair: style, colour, size", Category.COMBAT, true); }

    public boolean visibleNow() { return mc.options.getCameraType().isFirstPerson() || thirdPerson.on(); }

    public void draw(GuiGraphics g) {
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int s = size.i(), gp = gap.i(), t = thickness.i(), h = t / 2;
        int rgb = COLORS[color.get()];
        if (targetColor.on() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) rgb = 0xFF3B3B;
        int c = ((int) (opacity.get() * 255) << 24) | rgb;
        switch (style.get()) {
            case 1 -> bar(g, cx - h - 1, cy - h - 1, t + 2, t + 2, c);
            case 2 -> {
                int r = s + gp;
                bar(g, cx - r, cy - r, r * 2 + t, t, c); bar(g, cx - r, cy + r, r * 2 + t, t, c);
                bar(g, cx - r, cy - r, t, r * 2 + t, c); bar(g, cx + r, cy - r, t, r * 2 + t, c);
            }
            case 3 -> {
                int r = s + gp;
                for (int a = 0; a < 360; a += 6) {
                    double rad = Math.toRadians(a);
                    int px = cx + (int) Math.round(Math.cos(rad) * r), py = cy + (int) Math.round(Math.sin(rad) * r);
                    bar(g, px - h, py - h, t, t, c);
                }
            }
            case 4 -> {
                bar(g, cx - h, cy + gp + 1 - h, t, s, c);
                bar(g, cx - gp - s - h, cy - h, s, t, c);
                bar(g, cx + gp + 1 - h, cy - h, s, t, c);
            }
            case 5 -> {
                for (int i = gp; i < gp + s; i++) {
                    bar(g, cx + i - h, cy + i - h, t, t, c); bar(g, cx - i - h, cy + i - h, t, t, c);
                    bar(g, cx + i - h, cy - i - h, t, t, c); bar(g, cx - i - h, cy - i - h, t, t, c);
                }
            }
            default -> {
                bar(g, cx - h, cy - gp - s - h, t, s, c);
                bar(g, cx - h, cy + gp + 1 - h + (t > 1 ? 1 : 0), t, s, c);
                bar(g, cx - gp - s - h, cy - h, s, t, c);
                bar(g, cx + gp + 1 - h + (t > 1 ? 1 : 0), cy - h, s, t, c);
                if (style.get() == 6) bar(g, cx - h, cy - h, t, t, c);
            }
        }
        if (dot.on() && style.get() != 1) bar(g, cx - h, cy - h, t, t, c);
    }

    private void bar(GuiGraphics g, int x, int y, int w, int hgt, int c) {
        if (outline.on()) g.fill(x - 1, y - 1, x + w + 1, y + hgt + 1, ((c >>> 24) * 3 / 4) << 24);
        g.fill(x, y, x + w, y + hgt, c);
    }
}
