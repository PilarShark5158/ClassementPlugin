package fr.classement;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/** Retrouve la ville d'un joueur : Towny (réflexion) ou villes internes. */
public class TownProvider {
    private final ClassementPlugin plugin;
    private final RankingManager manager;

    public TownProvider(ClassementPlugin plugin, RankingManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public String townOf(Player p) {
        String mode = plugin.getConfig().getString("town-provider", "auto").toLowerCase(Locale.ROOT);
        Plugin towny = Bukkit.getPluginManager().getPlugin("Towny");
        boolean useTowny = mode.equals("towny") || (mode.equals("auto") && towny != null && towny.isEnabled());
        if (useTowny) {
            if (towny == null) return null;
            try {
                Class<?> api = towny.getClass().getClassLoader().loadClass("com.palmergames.bukkit.towny.TownyAPI");
                Object inst = api.getMethod("getInstance").invoke(null);
                Object t = api.getMethod("getTown", Player.class).invoke(inst, p);
                if (t == null) return null;
                Object name = t.getClass().getMethod("getName").invoke(t);
                return name == null ? null : name.toString();
            } catch (Throwable ex) {
                return null;
            }
        }
        return manager.internalTown(p.getUniqueId());
    }
}
