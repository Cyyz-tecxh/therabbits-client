package com.therabbits.client.module.hud;

public class BiomeModule extends TextHudModule {
    public BiomeModule() { super("Biome", "Shows the biome you are in", false, 300, 58); }

    @Override
    protected String text(boolean editing) {
        if (mc.player == null || mc.level == null) return "Plains";
        String id = mc.level.getBiome(mc.player.blockPosition()).getRegisteredName();
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
