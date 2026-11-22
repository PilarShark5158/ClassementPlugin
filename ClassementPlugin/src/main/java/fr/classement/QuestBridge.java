package fr.classement;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compte les quêtes terminées de QuestPlugin (sans dépendance de compilation) :
 * une quête compte dès que sa progression atteint l'objectif, ou qu'elle est réclamée.
 * On ajoute au classement chaque hausse du nombre de quêtes terminées.
 */
public class QuestBridge {
    private final ClassementPlugin plugin;
    private final Map<UUID, Integer> last = new HashMap<>();
    private boolean failed;

    public QuestBridge(ClassementPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        failed = false;
        last.clear();
    }

    private Map<UUID, Integer> scan() throws Exception {
        Plugin qp = Bukkit.getPluginManager().getPlugin("QuestPlugin");
        if (qp == null || !qp.isEnabled()) throw new IllegalStateException("QuestPlugin introuvable ou désactivé");
        Object mgr = qp.getClass().getMethod("manager").invoke(qp);
        java.lang.reflect.Field df = mgr.getClass().getDeclaredField("data");
        df.setAccessible(true);
        java.lang.reflect.Field qf = mgr.getClass().getDeclaredField("quests");
        qf.setAccessible(true);
        Map<?, ?> data = (Map<?, ?>) df.get(mgr);
        Map<?, ?> quests = (Map<?, ?>) qf.get(mgr);

        Map<UUID, Integer> out = new HashMap<>();
        for (Map.Entry<?, ?> en : new ArrayList<>(data.entrySet())) {
            if (!(en.getKey() instanceof UUID uuid) || !(en.getValue() instanceof Map<?, ?> prog)) continue;
            int done = 0;
            for (Map.Entry<?, ?> pe : new ArrayList<>(prog.entrySet())) {
                Object pr = pe.getValue();
                if (pr == null) continue;
                boolean claimed = pr.getClass().getField("claimed").getBoolean(pr);
                int amount = pr.getClass().getField("amount").getInt(pr);
                int need = Integer.MAX_VALUE;
                Object q = quests.get(pe.getKey());
                if (q != null) need = q.getClass().getField("amount").getInt(q);
                if (claimed || amount >= need) done++;
            }
            out.put(uuid, done);
        }
        return out;
    }

    public void poll() {
        if (failed || !plugin.getConfig().getBoolean("quest-bridge", true)) return;
        if (Bukkit.getPluginManager().getPlugin("QuestPlugin") == null) return;
        try {
            for (Map.Entry<UUID, Integer> en : scan().entrySet()) {
                Integer before = last.put(en.getKey(), en.getValue());
                if (before != null && en.getValue() > before) {
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p != null) plugin.manager().add(p, StatType.QUESTS_DONE, null, en.getValue() - before);
                }
            }
        } catch (Throwable ex) {
            failed = true;
            plugin.getLogger().warning("Liaison QuestPlugin impossible : " + ex
                    + " -> utilise /classementadmin quetes pour le détail.");
        }
    }

    public List<String> debug(Player p) {
        List<String> out = new ArrayList<>();
        out.add("QuestPlugin présent : " + (Bukkit.getPluginManager().getPlugin("QuestPlugin") != null));
        out.add("quest-bridge activé : " + plugin.getConfig().getBoolean("quest-bridge", true));
        out.add("Pont en erreur : " + failed);
        try {
            Map<UUID, Integer> s = scan();
            out.add("Joueurs suivis par QuestPlugin : " + s.size());
            if (p != null) {
                out.add("Tes quêtes terminées vues par le pont : " + s.getOrDefault(p.getUniqueId(), 0));
                out.add("Dernière valeur mémorisée : " + last.get(p.getUniqueId()));
            }
        } catch (Throwable ex) {
            out.add("Erreur de lecture : " + ex);
        }
        return out;
    }
}
