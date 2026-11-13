package com.therabbits.client.module;

import com.therabbits.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/** A module that draws a movable box on the HUD. Position is stored in scaled GUI pixels. */
public abstract class HudModule extends Module {
    public double x, y;
    public final NumberSetting scale;

    protected HudModule(String name, String description, boolean enabledByDefault, double x, double y) {
        super(name, description, Category.HUD, enabledByDefault);
        this.x = x;
        this.y = y;
        this.scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.05));
    }

    public abstract int width();
    public abstract int height();

    /** Draw at local 0,0 (already translated and scaled). */
    public abstract void render(GuiGraphics g, boolean editing);

    public int scaledWidth() { return (int) Math.ceil(width() * scale.get()); }
    public int scaledHeight() { return (int) Math.ceil(height() * scale.get()); }
}
