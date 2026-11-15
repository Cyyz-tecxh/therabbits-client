package com.therabbits.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.therabbits.client.hud.HudColors;

/**
 * Any colour (hex / colour picker), optional rainbow "Chroma", and for per-element settings
 * an optional "Global" mode that follows HUD Style. Reads old palette names from older configs.
 */
public class ColorSetting extends Setting<Integer> {
    private final boolean allowGlobal;
    private boolean global, chroma;
    private final int defRgb;
    private final boolean defGlobal;

    public ColorSetting(String name, int rgb, boolean allowGlobal, boolean startGlobal) {
        super(name, rgb & 0xFFFFFF);
        this.allowGlobal = allowGlobal;
        this.global = allowGlobal && startGlobal;
        this.defRgb = rgb & 0xFFFFFF;
        this.defGlobal = this.global;
    }

    public boolean allowGlobal() { return allowGlobal; }
    public boolean global() { return allowGlobal && global; }
    public void setGlobal(boolean g) { global = allowGlobal && g; }
    public boolean chroma() { return chroma; }
    public void setChroma(boolean c) { chroma = c; if (c) global = false; }
    public int rgb() { return value & 0xFFFFFF; }
    public void setRgb(int rgb) { value = rgb & 0xFFFFFF; global = false; chroma = false; }

    /** Current colour (no alpha); offset staggers Chroma between lines. */
    public int color(float offset) {
        if (chroma) {
            float hue = ((System.currentTimeMillis() % 4000) / 4000f + offset) % 1f;
            return java.awt.Color.HSBtoRGB(hue, 0.65f, 1f) & 0xFFFFFF;
        }
        return rgb();
    }

    public String hex() { return String.format("#%06X", rgb()); }

    /** Accepts "#RRGGBB", "RRGGBB" or "#RGB". Returns false if it isn't a colour. */
    public boolean setHex(String s) {
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        if (!s.matches("[0-9a-fA-F]{6}")) return false;
        setRgb(Integer.parseInt(s, 16));
        return true;
    }

    public boolean isDefault() { return global == defGlobal && !chroma && (global || rgb() == defRgb); }
    public void reset() { value = defRgb; global = defGlobal; chroma = false; }

    public String display() { return global() ? "Global" : chroma ? "Chroma" : hex(); }

    @Override public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("rgb", hex());
        o.addProperty("global", global());
        o.addProperty("chroma", chroma);
        return o;
    }

    @Override public void load(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("rgb")) setHex(o.get("rgb").getAsString());
            chroma = o.has("chroma") && o.get("chroma").getAsBoolean();
            global = allowGlobal && o.has("global") && o.get("global").getAsBoolean();
        } else if (e.isJsonPrimitive()) {      // older configs stored a palette name
            String n = e.getAsString();
            if (n.equals("Global")) { global = allowGlobal; return; }
            int i = java.util.Arrays.asList(HudColors.NAMES).indexOf(n);
            if (i < 0) return;
            if (HudColors.NAMES[i].equals("Chroma")) { chroma = true; global = false; }
            else setRgb(HudColors.rgb(i, 0));
        }
    }
}
