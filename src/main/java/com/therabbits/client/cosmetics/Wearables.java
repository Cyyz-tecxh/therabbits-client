package com.therabbits.client.cosmetics;

/** The 3D cosmetics. Ids are what the cosmetics server stores. */
public final class Wearables {
    private Wearables() {}

    public static final String[] HAT_IDS = {"none", "fedora", "crown", "ears", "royal_crown", "gold_crown", "cat_hat", "bucket_hat", "santa_hat", "frog_hat", "bunny_hat"};
    public static final String[] HAT_NAMES = {"None", "Black Fedora", "Spiked Crown", "Rabbit Ears", "Royal Crown", "Gold Crown", "Cat Ears", "Bucket Hat", "Santa Hat", "Frog Hat", "Bunny Hat"};
    public static final String[] ARM_IDS = {"none", "claws"};
    public static final String[] ARM_NAMES = {"None", "Claws"};

    public static final String[] SHIELD_IDS = {"none", "neon_shield"};
    public static final String[] SHIELD_NAMES = {"None", "Neon Royal Shield"};

    public static final String[] AURA_IDS = {"none", "aura"};
    public static final String[] AURA_NAMES = {"None", "Electric Aura"};

    public static boolean validAura(String id) { return java.util.Arrays.asList(AURA_IDS).contains(id); }
    public static boolean validShield(String id) { return java.util.Arrays.asList(SHIELD_IDS).contains(id); }
    public static boolean validHat(String id) { return java.util.Arrays.asList(HAT_IDS).contains(id); }
    public static boolean validArms(String id) { return java.util.Arrays.asList(ARM_IDS).contains(id); }
}
