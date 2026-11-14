package com.therabbits.client.module;

public enum Category {
    HUD("HUD"),
    COMBAT("PvP"),
    VISUAL("Visual"),
    UTILITY("Utility"),
    COSMETICS("Cosmetics");

    private final String label;
    Category(String label) { this.label = label; }
    public String label() { return label; }
}
