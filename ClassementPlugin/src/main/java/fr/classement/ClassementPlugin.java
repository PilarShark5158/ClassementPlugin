package fr.classement;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClassementPlugin extends JavaPlugin implements TabExecutor {
    private RankingManager manager;
    private Menus menus;
    private QuestBridge questBridge;
    private EconomyBridge economyBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new RankingManager(this);
        manager.load();
        menus = new Menus(this);
        questBridge = new QuestBridge(this);
        economyBridge = new EconomyBridge(this);

        Bukkit.getPluginManager().registerEvents(new StatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        getCommand("classement").setExecutor(this);
        getCommand("classementadmin").setExecutor(this);
        getCommand("classementadmin").setTabCompleter(this);

        // Temps de jeu (+1 minute) et vérification du changement de semaine
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) manager.add(p, StatType.PLAYTIME, null, 1);
            manager.tickWeek();
        }, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, questBridge::poll, 200L, 200L);
        Bukkit.getScheduler().runTaskTimer(this, economyBridge::poll, 600L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, manager::saveData, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.saveData();
    }

    public RankingManager manager() {
        return manager;
    }

    public Menus menus() {
        return menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("classement")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Commande réservée aux joueurs.");
                return true;
            }
            menus.openMain(p);
            return true;
        }

        if (!sender.hasPermission("classement.admin")) {
            sender.sendMessage(Txt.c("<red>Tu n'as pas la permission."));
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player p) menus.openAdmin(p);
            else sender.sendMessage("Utilise: reset | reload | ville <joueur> <nom|none> | add <joueur> <classement> <valeur>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reloadConfig();
                manager.saveData();
                manager.load();
                questBridge.reset();
                sender.sendMessage(Txt.c("<green>Configuration rechargée."));
            }
            case "quetes" -> {
                for (String line : questBridge.debug(sender instanceof Player pl ? pl : null))
                    sender.sendMessage(Txt.c("<gray>" + line.replace("<", "\\<")));
            }
            case "reset" -> {
                manager.endWeek();
                sender.sendMessage(Txt.c("<green>Semaine terminée, récompenses distribuées."));
            }
            case "ville" -> {
                if (args.length < 3) {
                    sender.sendMessage(Txt.c("<red>/classementadmin ville <joueur> <nom|none>"));
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                if (args[2].equalsIgnoreCase("none")) {
                    manager.setInternalTown(op.getUniqueId(), null);
                    sender.sendMessage(Txt.c("<green>" + args[1] + " n'a plus de ville."));
                } else {
                    String town = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                    manager.setInternalTown(op.getUniqueId(), town);
                    sender.sendMessage(Txt.c("<green>" + args[1] + " appartient maintenant à la ville <yellow>" + town + "<green>."));
                }
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(Txt.c("<red>/classementadmin add <joueur> <classement> <valeur>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                Ranking r = manager.get(args[2]);
                if (target == null || r == null) {
                    sender.sendMessage(Txt.c("<red>Joueur en ligne ou classement introuvable."));
                    return true;
                }
                try {
                    long v = Long.parseLong(args[3]);
                    manager.add(target, r.type, r.type == StatType.KILL_MOB ? r.target.split(",")[0] : null, v);
                    sender.sendMessage(Txt.c("<green>+" + v + " ajouté à " + target.getName() + " (" + r.name + ")."));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Txt.c("<red>Valeur invalide."));
                }
            }
            default -> sender.sendMessage(Txt.c("<red>Sous-commandes : reset, reload, ville, add, quetes"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("classement.admin")) return out;
        if (args.length == 1) {
            for (String s : List.of("reset", "reload", "ville", "add", "quetes"))
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("ville") || args[0].equalsIgnoreCase("add"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            for (Ranking r : manager.all()) out.add(r.id);
        }
        return out;
    }
}
