package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.BoolSetting;
import com.therabbits.client.setting.ModeSetting;

/** Custom font for the client's menus and HUD (vanilla Minecraft text is untouched). */
public class ClientFontModule extends Module {
    public static final String[] NAMES = {"Exo 2", "Orbitron", "Rajdhani", "Poppins", "Minecraft"};
    public static final String[] FILES = {"exo2", "orbitron", "rajdhani", "poppins", null};
    private static ClientFontModule instance;

    public final ModeSetting font = add(new ModeSetting("Font", 0, false, NAMES));
    public final BoolSetting hud = add(new BoolSetting("Use On HUD", true));
    public final BoolSetting menus = add(new BoolSetting("Use In Menus", true));

    public ClientFontModule() { super("Client Font", "Exo 2, Orbitron, Rajdhani, Poppins or Minecraft", Category.VISUAL, true); instance = this; }

    public static ClientFontModule get() { return instance; }

    /** Font file id for the current choice, or null for the normal Minecraft font. */
    public String file() { return FILES[font.get()]; }
}
