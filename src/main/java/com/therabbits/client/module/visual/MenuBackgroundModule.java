package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.NumberSetting;
import net.minecraft.resources.Identifier;

/** Settings for the start-menu background. Image backgrounds first, then animated ones. */
public class MenuBackgroundModule extends Module {
    public record Image(String name, String file, int width, int height) {
        public Identifier texture() { return Identifier.fromNamespaceAndPath("therabbits", "textures/gui/bg/" + file + ".png"); }
    }

    public static final Image[] IMAGES = {
            new Image("Moonlit Lake", "moonlit_lake", 1307, 480),
            new Image("Sakura Temple", "sakura_temple", 1295, 480),
            new Image("Mono Pagoda", "mono_pagoda", 864, 480),
            new Image("Dark Cathedral", "dark_cathedral", 1307, 480),
            new Image("Sky Castle", "sky_castle", 1295, 480),
            new Image("Bamboo Shrine", "bamboo_shrine", 1299, 480),
            new Image("End Isles", "end_isles", 1299, 480),
            new Image("Floating Isles", "floating_isles", 858, 480),
            new Image("Void Castle", "void_castle", 858, 480),
            new Image("Violet Ruins", "violet_ruins", 866, 480),
            new Image("Blood Moon", "blood_moon", 864, 480),
            new Image("Sunset Pagoda", "sunset_pagoda", 864, 480),
            new Image("Frozen Peaks", "frozen_peaks", 864, 480),
            new Image("Emerald Ruins", "emerald_ruins", 866, 480),
            new Image("Bamboo Night", "bamboo_night", 860, 480),
    };
    public static final String[] ANIMATED = {"Grid", "Starfield", "Rain", "Snow", "Plain"};
    public static final int COUNT = IMAGES.length + ANIMATED.length;

    public final NumberSetting style = add(new NumberSetting("Background", IMAGES.length, 0, COUNT - 1, 1));
    public final BoolSetting grain = add(new BoolSetting("Film Grain", false));
    public final BoolSetting pan = add(new BoolSetting("Slow Camera Pan", true));
    public final BoolSetting lightning = add(new BoolSetting("Lightning", true));
    public final BoolSetting glow = add(new BoolSetting("Glow", true));

    public MenuBackgroundModule() { super("Menu Background", "Start menu background style", Category.VISUAL, true); }

    /** The image for the current style, or null for an animated style. */
    public Image image() { return style.i() < IMAGES.length ? IMAGES[style.i()] : null; }

    /** 0 Grid, 1 Starfield, 2 Rain, 3 Snow, 4 Plain; -1 when an image is selected. */
    public int animated() { return style.i() < IMAGES.length ? -1 : style.i() - IMAGES.length; }

    public String styleName() {
        Image img = image();
        return img != null ? img.name() : ANIMATED[animated()];
    }

    public void next() { style.set((double) ((style.i() + 1) % COUNT)); }
    public void previous() { style.set((double) ((style.i() - 1 + COUNT) % COUNT)); }
}
