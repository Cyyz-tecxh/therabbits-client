package com.therabbits.client.cosmetics;

/** The 3D cosmetics. Ids are what the cosmetics server stores. */
public final class Wearables {
    private Wearables() {}

    public static final String[] HAT_IDS = {"none", "fedora", "crown", "ears"};
    public static final String[] HAT_NAMES = {"None", "Black Fedora", "Spiked Crown", "Rabbit Ears"};
    public static final String[] ARM_IDS = {"none", "claws"};
    public static final String[] ARM_NAMES = {"None", "Claws"};

    public static boolean validHat(String id) { return java.util.Arrays.asList(HAT_IDS).contains(id); }
    public static boolean validArms(String id) { return java.util.Arrays.asList(ARM_IDS).contains(id); }
}
