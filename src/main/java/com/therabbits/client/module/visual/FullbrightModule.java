package com.therabbits.client.module.visual;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import net.minecraft.client.Minecraft;

/** See in the dark. OptionInstanceMixin returns a high gamma while this is on. */
public class FullbrightModule extends Module {
    private static FullbrightModule instance;

    public FullbrightModule() {
        super("Fullbright", "See in the dark", Category.VISUAL, false);
        instance = this;
    }

    public static boolean isActiveGamma(Object option) {
        if (instance == null || !instance.enabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.options != null && option == mc.options.gamma();
    }
}
