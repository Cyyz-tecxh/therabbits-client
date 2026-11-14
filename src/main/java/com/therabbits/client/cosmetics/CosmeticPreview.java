package com.therabbits.client.cosmetics;

import java.util.HashMap;
import java.util.Map;

/**
 * "Try on" state while the cosmetics screen is open: what you're previewing on yourself before equipping.
 * Slots: cape, hat, arms, shield, aura. A missing slot means "show what you have equipped".
 */
public final class CosmeticPreview {
    private CosmeticPreview() {}

    private static final Map<String, String> TRY = new HashMap<>();
    private static boolean active;

    public static void begin() { active = true; TRY.clear(); }
    public static void end() { active = false; TRY.clear(); }
    public static boolean active() { return active; }

    public static void set(String slot, String id) { TRY.put(slot, id); }
    public static void clear() { TRY.clear(); }
    public static boolean changed() { return !TRY.isEmpty(); }
    public static Map<String, String> all() { return Map.copyOf(TRY); }

    /** The id being tried on for this slot, or null to use the equipped one. */
    public static String get(String slot) { return active ? TRY.get(slot) : null; }
}
