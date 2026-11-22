package fr.classement;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RankingManager {
    public static final int MAX_RANKS = 9;

    private final ClassementPlugin plugin;
    private final File rankFile;
    private final File dataFile;
    private final TownProvider towns;

    private final Map<String, Ranking> rankings = new LinkedHashMap<>();
    private final Map<String, Map<UUID, Long>> solo = new HashMap<>();
    private final Map<String, Map<String, Long>> town = new HashMap<>();
    private final Map<String, Map<String, Map<UUID, Long>>> contrib = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, String> internalTowns = new HashMap<>();
    private final Map<UUID, Long> lastBalance = new HashMap<>();
    private String weekKey = "";
    private ZoneId zone = ZoneId.of("Europe/Paris");

    public RankingManager(ClassementPlugin plugin) {
        this.plugin = plugin;
        this.rankFile = new File(plugin.getDataFolder(), "classements.yml");
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.towns = new TownProvider(plugin, this);
    }

    // ------------------------------------------------------------------ chargement

    public void load() {
        plugin.getDataFolder().mkdirs();
        try {
            zone = ZoneId.of(plugin.getConfig().getString("timezone", "Europe/Paris"));
        } catch (Exception ex) {
            zone = ZoneId.of("Europe/Paris");
        }
        rankings.clear();
        solo.clear();
        town.clear();
        contrib.clear();
        names.clear();
        internalTowns.clear();
        lastBalance.clear();

        if (!rankFile.exists()) {
            createDefaults();
            saveRankings();
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(rankFile);
            ConfigurationSection sec = y.getConfigurationSection("rankings");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(id);
                    if (s == null) continue;
                    Ranking r = new Ranking(id);
                    r.name = s.getString("name", id);
                    try {
                        r.type = StatType.valueOf(s.getString("type", "PLAYTIME").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                    }
                    r.target = s.getString("target", "tout");
                    Material m = Material.matchMaterial(s.getString("icon", ""));
                    r.icon = m != null ? m : r.type.icon;
                    r.enabled = s.getBoolean("enabled", true);
                    loadRewards(s.getConfigurationSection("rewards.solo"), r.soloRewards);
                    loadRewards(s.getConfigurationSection("rewards.ville"), r.townRewards);
                    rankings.put(id, r);
                }
            }
        }

        YamlConfiguration d = YamlConfiguration.loadConfiguration(dataFile);
        weekKey = d.getString("week", "");
        ConfigurationSection ns = d.getConfigurationSection("names");
        if (ns != null) for (String k : ns.getKeys(false)) {
            UUID u = parse(k);
            if (u != null) names.put(u, ns.getString(k, "?"));
        }
        ConfigurationSection ts = d.getConfigurationSection("towns");
        if (ts != null) for (String k : ts.getKeys(false)) {
            UUID u = parse(k);
            if (u != null) internalTowns.put(u, ts.getString(k, ""));
        }
        ConfigurationSection bs = d.getConfigurationSection("lastbal");
        if (bs != null) for (String k : bs.getKeys(false)) {
            UUID u = parse(k);
            if (u != null) lastBalance.put(u, bs.getLong(k));
        }
        ConfigurationSection ss = d.getConfigurationSection("solo");
        if (ss != null) for (String cid : ss.getKeys(false)) {
            ConfigurationSection c = ss.getConfigurationSection(cid);
            if (c == null) continue;
            Map<UUID, Long> m = new HashMap<>();
            for (String k : c.getKeys(false)) {
                UUID u = parse(k);
                if (u != null) m.put(u, c.getLong(k));
            }
            solo.put(cid, m);
        }
        ConfigurationSection vs = d.getConfigurationSection("town");
        if (vs != null) for (String cid : vs.getKeys(false)) {
            ConfigurationSection c = vs.getConfigurationSection(cid);
            if (c == null) continue;
            Map<String, Long> m = new HashMap<>();
            for (String k : c.getKeys(false)) m.put(k, c.getLong(k));
            town.put(cid, m);
        }
        ConfigurationSection cs = d.getConfigurationSection("contrib");
        if (cs != null) for (String cid : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(cid);
            if (c == null) continue;
            Map<String, Map<UUID, Long>> byTown = new HashMap<>();
            for (String tn : c.getKeys(false)) {
                ConfigurationSection t = c.getConfigurationSection(tn);
                if (t == null) continue;
                Map<UUID, Long> m = new HashMap<>();
                for (String k : t.getKeys(false)) {
                    UUID u = parse(k);
                    if (u != null) m.put(u, t.getLong(k));
                }
                byTown.put(tn, m);
            }
            contrib.put(cid, byTown);
        }
        if (weekKey.isEmpty()) weekKey = currentWeekKey();
    }

    private UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void loadRewards(ConfigurationSection rs, Map<Integer, List<String>> out) {
        if (rs == null) return;
        for (String k : rs.getKeys(false)) {
            try {
                int rank = Integer.parseInt(k);
                List<String> l = rs.getStringList(k);
                if (!l.isEmpty()) out.put(rank, new ArrayList<>(l));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void createDefaults() {
        def("temps-de-jeu", "Temps de jeu", StatType.PLAYTIME);
        def("mobs", "Mobs tués", StatType.KILL_MOB);
        def("quetes", "Quêtes finies", StatType.QUESTS_DONE);
        def("argent", "Argent gagné", StatType.MONEY);
        def("bois", "Bois récolté", StatType.WOOD);
        def("poisson", "Poissons pêchés", StatType.FISH);
        def("cultures", "Cultures récoltées", StatType.CROP);
    }

    private void def(String id, String name, StatType type) {
        Ranking r = new Ranking(id);
        r.name = name;
        r.type = type;
        r.icon = type.icon;
        rankings.put(id, r);
    }

    // ------------------------------------------------------------------ sauvegarde

    public void saveRankings() {
        YamlConfiguration y = new YamlConfiguration();
        for (Ranking r : rankings.values()) {
            String b = "rankings." + r.id;
            y.set(b + ".name", r.name);
            y.set(b + ".type", r.type.name());
            y.set(b + ".target", r.target);
            y.set(b + ".icon", r.icon.name());
            y.set(b + ".enabled", r.enabled);
            for (Map.Entry<Integer, List<String>> e : r.soloRewards.entrySet())
                if (!e.getValue().isEmpty()) y.set(b + ".rewards.solo." + e.getKey(), e.getValue());
            for (Map.Entry<Integer, List<String>> e : r.townRewards.entrySet())
                if (!e.getValue().isEmpty()) y.set(b + ".rewards.ville." + e.getKey(), e.getValue());
        }
        try {
            y.save(rankFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder classements.yml : " + ex.getMessage());
        }
    }

    public void saveData() {
        YamlConfiguration d = new YamlConfiguration();
        d.set("week", weekKey);
        for (Map.Entry<UUID, String> e : names.entrySet()) d.set("names." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, String> e : internalTowns.entrySet()) d.set("towns." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Long> e : lastBalance.entrySet()) d.set("lastbal." + e.getKey(), e.getValue());
        for (Map.Entry<String, Map<UUID, Long>> c : solo.entrySet())
            for (Map.Entry<UUID, Long> e : c.getValue().entrySet())
                d.set("solo." + c.getKey() + "." + e.getKey(), e.getValue());
        for (Map.Entry<String, Map<String, Long>> c : town.entrySet())
            for (Map.Entry<String, Long> e : c.getValue().entrySet())
                d.set("town." + c.getKey() + "." + e.getKey(), e.getValue());
        for (Map.Entry<String, Map<String, Map<UUID, Long>>> c : contrib.entrySet())
            for (Map.Entry<String, Map<UUID, Long>> t : c.getValue().entrySet())
                for (Map.Entry<UUID, Long> e : t.getValue().entrySet())
                    d.set("contrib." + c.getKey() + "." + t.getKey() + "." + e.getKey(), e.getValue());
        try {
            d.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ classements (CRUD)

    public List<Ranking> all() {
        return new ArrayList<>(rankings.values());
    }

    public List<Ranking> enabled() {
        List<Ranking> out = new ArrayList<>();
        for (Ranking r : rankings.values()) if (r.enabled) out.add(r);
        return out;
    }

    public Ranking get(String id) {
        return rankings.get(id);
    }

    public Ranking create(String name) {
        String base = slug(name);
        String id = base;
        int i = 2;
        while (rankings.containsKey(id)) id = base + "-" + i++;
        Ranking r = new Ranking(id);
        r.name = name;
        r.icon = r.type.icon;
        rankings.put(id, r);
        saveRankings();
        return r;
    }

    public void remove(Ranking r) {
        rankings.remove(r.id);
        solo.remove(r.id);
        town.remove(r.id);
        contrib.remove(r.id);
        saveRankings();
        saveData();
    }

    private String slug(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return n.isEmpty() ? "classement" : n;
    }

    // ------------------------------------------------------------------ scores

    public void add(Player p, StatType type, String target, long amount) {
        if (amount <= 0) return;
        for (Ranking r : rankings.values()) {
            if (!r.enabled || r.type != type || !r.matches(target)) continue;
            addScore(r, p, amount);
        }
    }

    private void addScore(Ranking r, Player p, long amount) {
        UUID u = p.getUniqueId();
        names.put(u, p.getName());
        solo.computeIfAbsent(r.id, k -> new HashMap<>()).merge(u, amount, Long::sum);
        String t = townOf(p);
        if (t != null) {
            town.computeIfAbsent(r.id, k -> new HashMap<>()).merge(t, amount, Long::sum);
            contrib.computeIfAbsent(r.id, k -> new HashMap<>())
                    .computeIfAbsent(t, k -> new HashMap<>())
                    .merge(u, amount, Long::sum);
        }
    }

    public List<Map.Entry<String, Long>> top(Ranking r, boolean townMode) {
        List<Map.Entry<String, Long>> list = new ArrayList<>();
        if (townMode) {
            Map<String, Long> m = town.get(r.id);
            if (m != null) for (Map.Entry<String, Long> e : m.entrySet())
                if (e.getValue() > 0) list.add(Map.entry(e.getKey(), e.getValue()));
        } else {
            Map<UUID, Long> m = solo.get(r.id);
            if (m != null) for (Map.Entry<UUID, Long> e : m.entrySet())
                if (e.getValue() > 0) list.add(Map.entry(e.getKey().toString(), e.getValue()));
        }
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list;
    }

    public List<Map.Entry<UUID, Long>> contributors(Ranking r, String townName) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        Map<String, Map<UUID, Long>> byTown = contrib.get(r.id);
        if (byTown == null) return list;
        Map<UUID, Long> m = byTown.get(townName);
        if (m == null) return list;
        for (Map.Entry<UUID, Long> e : m.entrySet()) list.add(Map.entry(e.getKey(), e.getValue()));
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list;
    }

    public String nameOf(UUID u) {
        String n = names.get(u);
        if (n != null) return n;
        String o = Bukkit.getOfflinePlayer(u).getName();
        return o != null ? o : "Inconnu";
    }

    public void rememberName(Player p) {
        names.put(p.getUniqueId(), p.getName());
    }

    // ------------------------------------------------------------------ villes

    public String townOf(Player p) {
        String t = towns.townOf(p);
        return t == null || t.isBlank() ? null : t.replace('.', '_').trim();
    }

    public String internalTown(UUID u) {
        return internalTowns.get(u);
    }

    public void setInternalTown(UUID u, String name) {
        if (name == null) internalTowns.remove(u);
        else internalTowns.put(u, name);
    }

    // ------------------------------------------------------------------ argent (suivi du solde)

    public Long lastBalance(UUID u) {
        return lastBalance.get(u);
    }

    public void setLastBalance(UUID u, long v) {
        lastBalance.put(u, v);
    }

    // ------------------------------------------------------------------ semaine

    private String currentWeekKey() {
        LocalDate d = LocalDate.now(zone);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    public String weekLabel() {
        return weekKey;
    }

    public long millisUntilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone);
        return Duration.between(now, next).toMillis();
    }

    public void tickWeek() {
        if (!currentWeekKey().equals(weekKey)) endWeek();
    }

    /** Distribue les récompenses puis remet tous les classements à zéro. */
    public void endWeek() {
        for (Ranking r : rankings.values()) {
            if (!r.enabled) continue;
            distribute(r, false);
            distribute(r, true);
        }
        solo.clear();
        town.clear();
        contrib.clear();
        weekKey = currentWeekKey();
        saveData();
        if (plugin.getConfig().getBoolean("broadcast-reset", true)) {
            Bukkit.broadcast(Txt.c("<gold><bold>Classements</bold> <gray>» <yellow>Nouvelle semaine ! Les récompenses ont été distribuées et les classements remis à zéro."));
        }
    }

    private void distribute(Ranking r, boolean townMode) {
        Map<Integer, List<String>> rw = r.rewards(townMode);
        if (rw.isEmpty()) return;
        List<Map.Entry<String, Long>> top = top(r, townMode);
        for (int i = 0; i < top.size() && i < MAX_RANKS; i++) {
            int rank = i + 1;
            List<String> cmds = rw.get(rank);
            if (cmds == null || cmds.isEmpty()) continue;
            String key = top.get(i).getKey();
            Set<UUID> winners = new LinkedHashSet<>();
            if (townMode) {
                for (Map.Entry<UUID, Long> c : contributors(r, key)) winners.add(c.getKey());
            } else {
                UUID u = parse(key);
                if (u != null) winners.add(u);
            }
            for (UUID u : winners) {
                String pn = nameOf(u);
                for (String cmd : cmds) {
                    String run = cmd.trim();
                    if (run.startsWith("/")) run = run.substring(1);
                    run = run.replace("%player%", pn)
                            .replace("%rank%", String.valueOf(rank))
                            .replace("%classement%", r.name);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
                }
                Player online = Bukkit.getPlayer(u);
                if (online != null) {
                    online.sendMessage(Txt.c("<gold>Classements <gray>» <green>Tu termines <yellow>#" + rank
                            + " <green>du classement <yellow>" + r.name + (townMode ? " <gray>(Ville " + key + ")" : "")
                            + "<green> : récompense reçue !"));
                }
            }
        }
    }
}
