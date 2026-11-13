package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.gui.Theme;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;

/** HUD element that shows one line of text in a box. */
public abstract class TextHudModule extends HudModule {
    protected final BoolSetting background = add(new BoolSetting("Background", true));
    protected final BoolSetting shadow = add(new BoolSetting("Text Shadow", true));

    protected TextHudModule(String name, String description, boolean on, double x, double y) {
        super(name, description, on, x, y);
    }

    /** Text to show; editing = true when shown in the HUD editor (no world may be loaded). */
    protected abstract String text(boolean editing);

    private String last = "";

    @Override public int width() { return Math.max(40, Draw.font().width(last) + 10); }
    @Override public int height() { return 16; }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        last = text(editing);
        if (background.on()) Draw.rect(g, 0, 0, width(), height(), Theme.HUD_BG);
        int tx = width() / 2 - Draw.font().width(last) / 2;
        if (shadow.on()) Draw.textShadow(g, last, tx, 4, Theme.TEXT);
        else Draw.text(g, last, tx, 4, Theme.TEXT);
    }
}
