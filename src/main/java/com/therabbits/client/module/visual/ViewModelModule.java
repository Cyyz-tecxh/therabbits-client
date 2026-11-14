package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;
import com.therabbits.client.setting.NumberSetting;

/** Moves, scales and rotates the items in your hands (ItemInHandRendererMixin). */
public class ViewModelModule extends Module {
    private static ViewModelModule instance;
    public final NumberSetting x = add(new NumberSetting("X", 0, -1, 1, 0.02));
    public final NumberSetting y = add(new NumberSetting("Y", 0, -1, 1, 0.02));
    public final NumberSetting z = add(new NumberSetting("Z", 0, -1, 1, 0.02));
    public final NumberSetting scale = add(new NumberSetting("Scale", 1, 0.3, 2, 0.05));
    public final NumberSetting rotX = add(new NumberSetting("Rotate X", 0, -180, 180, 5));
    public final NumberSetting rotY = add(new NumberSetting("Rotate Y", 0, -180, 180, 5));
    public final NumberSetting rotZ = add(new NumberSetting("Rotate Z", 0, -180, 180, 5));

    public ViewModelModule() { super("ViewModel", "Move your hand items", Category.VISUAL, false); instance = this; }

    public static ViewModelModule get() { return instance; }
}
