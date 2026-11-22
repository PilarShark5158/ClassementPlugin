package fr.classement;

import org.bukkit.Material;

import java.util.Locale;

public enum StatType {
    PLAYTIME("Temps de jeu", Material.CLOCK, ""),
    KILL_MOB("Mobs tués", Material.IRON_SWORD, " mobs"),
    QUESTS_DONE("Quêtes finies", Material.WRITABLE_BOOK, " quêtes"),
    MONEY("Argent gagné", Material.GOLD_INGOT, " $"),
    WOOD("Bois récolté", Material.OAK_LOG, " bûches"),
    FISH("Poissons pêchés", Material.FISHING_ROD, " poissons"),
    CROP("Cultures récoltées", Material.WHEAT, " cultures");

    public final String label;
    public final Material icon;
    private final String unit;

    StatType(String label, Material icon, String unit) {
        this.label = label;
        this.icon = icon;
        this.unit = unit;
    }

    public StatType next() {
        StatType[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public StatType prev() {
        StatType[] v = values();
        return v[(ordinal() + v.length - 1) % v.length];
    }

    public String format(long value) {
        if (this == PLAYTIME) {
            long h = value / 60;
            long m = value % 60;
            return h > 0 ? h + "h " + m + "m" : m + "m";
        }
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ') + unit;
    }
}
