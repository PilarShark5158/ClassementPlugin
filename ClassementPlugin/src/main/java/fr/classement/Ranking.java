package fr.classement;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;

public class Ranking {
    public final String id;
    public String name;
    public StatType type = StatType.PLAYTIME;
    public String target = "tout";
    public Material icon = Material.CLOCK;
    public boolean enabled = true;
    public final Map<Integer, List<String>> soloRewards = new TreeMap<>();
    public final Map<Integer, List<String>> townRewards = new TreeMap<>();

    public Ranking(String id) {
        this.id = id;
        this.name = id;
    }

    public Map<Integer, List<String>> rewards(boolean town) {
        return town ? townRewards : soloRewards;
    }

    /** Cible acceptée (utile pour KILL_MOB : "zombie", "sheep,cow", "tout"). */
    public boolean matches(String eventTarget) {
        if (type != StatType.KILL_MOB) return true;
        String t = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("tout") || t.equals("any")) return true;
        if (eventTarget == null) return false;
        String ev = eventTarget.toLowerCase(Locale.ROOT);
        for (String part : t.split(",")) {
            String p = part.trim().replace("minecraft:", "");
            if (p.equals(ev)) return true;
        }
        return false;
    }

    public List<String> rewardList(boolean town, int rank) {
        return rewards(town).computeIfAbsent(rank, k -> new ArrayList<>());
    }
}
