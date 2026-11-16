package com.therabbits.client.cosmetics;

/** The 3D cosmetics. Ids are what the cosmetics server stores. */
public final class Wearables {
    private Wearables() {}

    public static final String[] HAT_IDS = {"none", "oni_mask", "kitsune_mask", "oni_menpo"};
    public static final String[] HAT_NAMES = {"None", "Oni Mask", "Kitsune Mask", "Oni Menpo"};
    public static final String[] ARM_IDS = {"none", "claws"};
    public static final String[] ARM_NAMES = {"None", "Claws"};

    public static final String[] SHIELD_IDS = {"none", "neon_shield", "blood_moon_shield", "decoding_shield"};
    public static final String[] SHIELD_NAMES = {"None", "Neon Royal Shield", "Blood Moon Shield", "Decoding Shield"};

    public static final String[] AURA_IDS = {"none", "aura", "snow_aura", "glyph_aura", "candy_aura", "clockwork_aura", "petal_aura", "koi_aura",
        "cards_blood", "cards_royal", "cards_frost", "cards_heartbreaker", "cards_void", "cards_emerald",
        "dragon_eternal", "dragon_shadow", "dragon_frost", "dragon_infernal", "dragon_celestial"};
    public static final String[] AURA_NAMES = {"None", "Electric Aura", "Snow Aura", "NULL//PROTOCOL", "Candy Canes", "Clockwork Orbit", "Petal Storm", "Koi Spirits",
        "Card Master: Blood", "Card Master: Royal", "Card Master: Frost", "Card Master: Heartbreaker", "Card Master: Void", "Card Master: Emerald",
        "Eternal Dragon Aura", "Shadow Dragon Aura", "Frost Dragon Aura", "Infernal Dragon Aura", "Celestial Dragon Aura"};

    public static final String[] PET_IDS = {"none", "pet_eternal", "pet_shadow", "pet_frost", "pet_infernal", "pet_celestial", "spirit_bunny"};
    public static final String[] PET_NAMES = {"None", "Eternal Dragon", "Shadow Dragon", "Frost Dragon", "Infernal Dragon", "Celestial Dragon", "Spirit Bunny"};

    public static boolean validPet(String id) { return java.util.Arrays.asList(PET_IDS).contains(id); }
    public static boolean validAura(String id) { return java.util.Arrays.asList(AURA_IDS).contains(id); }
    public static boolean validShield(String id) { return java.util.Arrays.asList(SHIELD_IDS).contains(id); }
    public static boolean validHat(String id) { return java.util.Arrays.asList(HAT_IDS).contains(id); }
    public static boolean validArms(String id) { return java.util.Arrays.asList(ARM_IDS).contains(id); }
}
