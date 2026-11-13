package com.therabbits.client.module.visual;

import com.therabbits.client.module.Category;
import com.therabbits.client.module.Module;

/** Stops the screen shaking when you take damage (GameRendererMixin). */
public class NoHurtCamModule extends Module {
    public NoHurtCamModule() { super("No Hurt Cam", "No screen shake when you get hit", Category.VISUAL, true); }
}
