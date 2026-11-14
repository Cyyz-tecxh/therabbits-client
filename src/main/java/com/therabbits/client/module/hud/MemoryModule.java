package com.therabbits.client.module.hud;

public class MemoryModule extends TextHudModule {
    public MemoryModule() { super("Memory", "RAM usage", false, 300, 40); }

    @Override
    protected String text(boolean editing) {
        Runtime r = Runtime.getRuntime();
        long used = (r.totalMemory() - r.freeMemory()) / 1048576, max = r.maxMemory() / 1048576;
        return "RAM " + used + "/" + max + " MB";
    }
}
