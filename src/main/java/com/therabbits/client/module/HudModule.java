package com.therabbits.client.module;

import com.therabbits.client.hud.HudColors;
import com.therabbits.client.hud.HudStyle;
import com.therabbits.client.setting.ModeSetting;
import com.therabbits.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/** A module that draws a movable box on the HUD. Position is stored in scaled GUI pixels. */
public abstract class HudModule extends Module {
    public double x, y;
    public final NumberSetting scale;
    public final ModeSetting textColor;

    protected HudModule(String name, String description, boolean enabledByDefault, double x, double y) {
        super(name, description, Category.HUD, enabledByDefault);
        this.x = x;
        this.y = y;
        this.scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.05));
        this.textColor = add(new ModeSetting("Text Color", 0, true, HudColors.withGlobal()));
    }

    public abstract int width();
    public abstract int height();

    /** Draw at local 0,0 (already translated and scaled). */
    public abstract void render(GuiGraphics g, boolean editing);

    /** Element scale times the global HUD text size. */
    public float effectiveScale() { return scale.f() * HudStyle.size(); }

    public int scaledWidth() { return (int) Math.ceil(width() * effectiveScale()); }
    public int scaledHeight() { return (int) Math.ceil(height() * effectiveScale()); }
}
