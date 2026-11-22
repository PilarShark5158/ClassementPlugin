package fr.classement;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class Menus {
    /** Cases utilisées pour les icônes de classements (3 lignes de 7, comme la capture). */
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final ClassementPlugin plugin;
    private final RankingManager mgr;
    private final Map<UUID, Consumer<String>> prompts = new ConcurrentHashMap<>();

    public Menus(ClassementPlugin plugin) {
        this.plugin = plugin;
        this.mgr = plugin.manager();
    }

    // ------------------------------------------------------------------ saisie dans le chat

    public void prompt(Player p, String message, Consumer<String> cb) {
        p.closeInventory();
        prompts.put(p.getUniqueId(), s -> {
            if (s.trim().equalsIgnoreCase("annuler")) {
                p.sendMessage(Txt.c("<red>Annulé."));
            } else {
                cb.accept(s.trim());
            }
        });
        p.sendMessage(Txt.c("<gold>Classements <gray>» <yellow>" + message));
        p.sendMessage(Txt.c("<gray>Écris <red>annuler</red> pour annuler."));
    }

    public Consumer<String> takePrompt(UUID u) {
        return prompts.remove(u);
    }

    // ------------------------------------------------------------------ outils

    private String rankColor(int rank) {
        return switch (rank) {
            case 1 -> "<gold>";
            case 2 -> "<gray>";
            case 3 -> "<#cd7f32>";
            default -> "<yellow>";
        };
    }

    /** Retourne {position, score} du joueur, ou null s'il n'est pas classé. */
    private long[] mine(Ranking r, boolean town, Player p) {
        String key = town ? mgr.townOf(p) : p.getUniqueId().toString();
        if (key == null) return null;
        List<Map.Entry<String, Long>> top = mgr.top(r, town);
        for (int i = 0; i < top.size(); i++)
            if (top.get(i).getKey().equals(key)) return new long[]{i + 1, top.get(i).getValue()};
        return null;
    }

    private String label(Map.Entry<String, Long> e, boolean town) {
        return town ? e.getKey() : mgr.nameOf(UUID.fromString(e.getKey()));
    }

    private ItemStack back(String text) {
        return Txt.item(Material.ARROW, "<red>" + text);
    }

    private ItemStack timerItem(String... extra) {
        List<String> lore = new ArrayList<>(List.of(extra));
        lore.add("<gray>Reset dans : <white>" + Txt.duration(mgr.millisUntilReset()));
        lore.add("<gray>Chaque lundi à minuit.");
        return Txt.item(Material.OAK_SIGN, "<gold><bold>Classements de la semaine", lore);
    }

    // ------------------------------------------------------------------ menu principal (image 2)

    public void openMain(Player p) {
        Gui g = new Gui(54, "Classements");
        g.set(4, timerItem());
        g.set(21, Txt.head(p, "<green><bold>Classement Solo",
                List.of("<gray>Le classement des joueurs", "<gray>pour chaque catégorie.", "", "<yellow>Clique pour ouvrir")),
                e -> openList(p, false));
        g.set(23, Txt.item(Material.RED_BANNER, "<red><bold>Classement Ville",
                "<gray>Le classement des villes :", "<gray>les points de tous les membres", "<gray>sont additionnés.", "", "<yellow>Clique pour ouvrir"),
                e -> openList(p, true));
        g.set(49, back("Fermer"), e -> p.closeInventory());
        g.open(p);
    }

    // ------------------------------------------------------------------ liste des classements (image 1)

    public void openList(Player p, boolean town) {
        Gui g = new Gui(54, town ? "Classements Villes" : "Classements Joueurs");
        List<Ranking> rs = mgr.enabled();

        g.set(4, Txt.item(Material.GOLD_BLOCK, "<gold><bold>" + (town ? "Classements Villes" : "Classements Joueurs"),
                "<gray>Clique sur une catégorie pour voir", "<gray>le classement complet de la semaine."));

        for (int i = 0; i < rs.size() && i < SLOTS.length; i++) {
            Ranking r = rs.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>" + r.type.label);
            lore.add("");
            List<Map.Entry<String, Long>> top = mgr.top(r, town);
            if (top.isEmpty()) lore.add("<gray>Personne n'est classé pour l'instant.");
            for (int k = 0; k < 3 && k < top.size(); k++) {
                lore.add(rankColor(k + 1) + "#" + (k + 1) + " <white>" + label(top.get(k), town)
                        + " <dark_gray>- <yellow>" + r.type.format(top.get(k).getValue()));
            }
            lore.add("");
            long[] me = mine(r, town, p);
            if (me != null) lore.add("<green>Toi : <white>#" + me[0] + " <dark_gray>- <yellow>" + r.type.format(me[1]));
            else lore.add("<gray>Toi : non classé");
            lore.add("");
            lore.add("<yellow>Clique pour voir le classement complet");
            g.set(SLOTS[i], Txt.item(r.icon, "<yellow><bold>" + r.name, lore), e -> openDetail(p, r, town, 0));
        }

        g.set(48, timerItem());
        g.set(49, back("Retour"), e -> openMain(p));
        g.set(50, Txt.item(Material.EMERALD, "<green><bold>Récompenses",
                "<gray>Les meilleurs de chaque classement", "<gray>reçoivent une récompense", "<gray>à la fin de la semaine."));
        g.open(p);
    }

    // ------------------------------------------------------------------ détail d'un classement

    public void openDetail(Player p, Ranking r, boolean town, int page) {
        List<Map.Entry<String, Long>> top = mgr.top(r, town);
        int per = 36;
        int pages = Math.max(1, (top.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Gui g = new Gui(54, "<dark_gray>" + r.name + " <gray>(" + (town ? "Ville" : "Solo") + ")");

        g.set(4, Txt.item(r.icon, "<yellow><bold>" + r.name, "<dark_gray>" + r.type.label));

        String myKey = town ? mgr.townOf(p) : p.getUniqueId().toString();
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= top.size()) break;
            int rank = idx + 1;
            Map.Entry<String, Long> en = top.get(idx);
            boolean isMe = en.getKey().equals(myKey);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Score : <yellow>" + r.type.format(en.getValue()));
            List<String> rw = r.rewards(town).get(rank);
            if (rw != null && !rw.isEmpty()) lore.add("<green>Récompense en fin de semaine");
            ItemStack it;
            if (town) {
                lore.add("");
                lore.add("<gray>Meilleurs contributeurs :");
                List<Map.Entry<UUID, Long>> cs = mgr.contributors(r, en.getKey());
                for (int k = 0; k < 5 && k < cs.size(); k++)
                    lore.add("<dark_gray> - <white>" + mgr.nameOf(cs.get(k).getKey()) + " <yellow>" + r.type.format(cs.get(k).getValue()));
                if (isMe) lore.add("<green>← Ta ville");
                it = Txt.item(Material.WHITE_BANNER, rankColor(rank) + "#" + rank + " <white>" + en.getKey(), lore);
            } else {
                if (isMe) lore.add("<green>← C'est toi !");
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(en.getKey()));
                it = Txt.head(op, rankColor(rank) + "#" + rank + " <white>" + mgr.nameOf(op.getUniqueId()), lore);
            }
            g.set(9 + i, it);
        }

        if (pg > 0) g.set(45, Txt.item(Material.ARROW, "<yellow>Page précédente"), e -> openDetail(p, r, town, pg - 1));
        if (pg < pages - 1) g.set(53, Txt.item(Material.ARROW, "<yellow>Page suivante"), e -> openDetail(p, r, town, pg + 1));

        long[] me = mine(r, town, p);
        List<String> info = new ArrayList<>();
        info.add(me != null ? "<green>" + (town ? "Ta ville" : "Toi") + " : <white>#" + me[0] + " <dark_gray>- <yellow>" + r.type.format(me[1])
                : "<gray>" + (town ? "Ta ville" : "Toi") + " : non classé" + (town && myKey == null ? " (tu n'as pas de ville)" : ""));
        info.add("<gray>Page <white>" + (pg + 1) + "/" + pages);
        g.set(48, timerItem(info.toArray(new String[0])));

        g.set(49, back("Retour"), e -> openList(p, town));

        List<String> rl = new ArrayList<>();
        for (int rank = 1; rank <= RankingManager.MAX_RANKS; rank++) {
            List<String> c = r.rewards(town).get(rank);
            if (c != null && !c.isEmpty()) rl.add(rankColor(rank) + "#" + rank + " <gray>: <green>récompense");
        }
        if (rl.isEmpty()) rl.add("<gray>Aucune récompense définie.");
        g.set(50, Txt.item(Material.EMERALD, "<green><bold>Récompenses", rl));
        g.open(p);
    }

    // ------------------------------------------------------------------ admin : liste

    public void openAdmin(Player p) {
        Gui g = new Gui(54, "Admin - Classements");
        g.set(4, timerItem("<gray>Semaine : <white>" + mgr.weekLabel()));
        List<Ranking> rs = mgr.all();
        for (int i = 0; i < rs.size() && i < SLOTS.length; i++) {
            Ranking r = rs.get(i);
            g.set(SLOTS[i], Txt.item(r.icon, (r.enabled ? "<green>" : "<red>") + r.name,
                    "<dark_gray>id : " + r.id,
                    "<gray>Type : <white>" + r.type.label,
                    "<gray>Actif : " + (r.enabled ? "<green>oui" : "<red>non"),
                    "", "<yellow>Clique pour modifier"),
                    e -> openEdit(p, r));
        }
        g.set(45, Txt.item(Material.TNT, "<red><bold>Terminer la semaine maintenant",
                "<gray>Distribue les récompenses puis", "<gray>remet tous les classements à zéro.", "", "<red>Shift + clic pour confirmer"),
                e -> {
                    if (e.getClick() != ClickType.SHIFT_LEFT && e.getClick() != ClickType.SHIFT_RIGHT) {
                        p.sendMessage(Txt.c("<red>Fais Shift + clic pour confirmer."));
                        return;
                    }
                    mgr.endWeek();
                    p.sendMessage(Txt.c("<green>Semaine terminée, récompenses distribuées."));
                    openAdmin(p);
                });
        g.set(49, back("Fermer"), e -> p.closeInventory());
        g.set(53, Txt.item(Material.EMERALD_BLOCK, "<green><bold>Créer un classement", "<gray>Tu choisiras ensuite le type,", "<gray>l'icône et les récompenses."),
                e -> prompt(p, "Écris le nom du nouveau classement :", s -> {
                    Ranking r = mgr.create(s);
                    openEdit(p, r);
                }));
        g.open(p);
    }

    // ------------------------------------------------------------------ admin : édition

    public void openEdit(Player p, Ranking r) {
        Gui g = new Gui(27, "Admin - " + r.name);

        g.set(10, Txt.item(r.type.icon, "<yellow>Type : <white>" + r.type.label,
                "<gray>Clic gauche : type suivant", "<gray>Clic droit : type précédent"),
                e -> {
                    boolean defaultIcon = r.icon == r.type.icon;
                    r.type = e.getClick().isRightClick() ? r.type.prev() : r.type.next();
                    if (defaultIcon) r.icon = r.type.icon;
                    mgr.saveRankings();
                    openEdit(p, r);
                });

        List<String> tl = new ArrayList<>();
        tl.add("<gray>Cible : <white>" + r.target);
        if (r.type == StatType.KILL_MOB) {
            tl.add("<gray>Ex : zombie, sheep, cow,pig, tout");
            tl.add("");
            tl.add("<yellow>Clique pour modifier");
        } else {
            tl.add("<dark_gray>Utilisé uniquement pour « Mobs tués ».");
        }
        g.set(11, Txt.item(Material.NAME_TAG, "<yellow>Cible (mobs)", tl), e -> {
            if (r.type != StatType.KILL_MOB) {
                p.sendMessage(Txt.c("<red>La cible ne sert que pour le type « Mobs tués »."));
                return;
            }
            prompt(p, "Écris l'entité à tuer (ex : zombie, sheep, cow,pig, ou tout) :", s -> {
                r.target = s.toLowerCase();
                mgr.saveRankings();
                openEdit(p, r);
            });
        });

        g.set(12, Txt.item(r.icon, "<yellow>Icône : <white>" + r.icon.name(),
                "<gray>Tiens un objet en main puis clique", "<gray>pour l'utiliser comme icône."),
                e -> {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand.getType() == Material.AIR) {
                        p.sendMessage(Txt.c("<red>Tiens un objet dans ta main principale."));
                        return;
                    }
                    r.icon = hand.getType();
                    mgr.saveRankings();
                    openEdit(p, r);
                });

        g.set(13, Txt.item(Material.WRITABLE_BOOK, "<yellow>Nom : <white>" + r.name,
                "<gray>Couleurs MiniMessage acceptées", "<gray>(ex : <gold>Mobs</gold>)", "", "<yellow>Clique pour renommer"),
                e -> prompt(p, "Écris le nouveau nom :", s -> {
                    r.name = s;
                    mgr.saveRankings();
                    openEdit(p, r);
                }));

        g.set(14, Txt.item(r.enabled ? Material.LIME_DYE : Material.GRAY_DYE, r.enabled ? "<green>Classement actif" : "<red>Classement désactivé",
                "<gray>Clique pour " + (r.enabled ? "désactiver" : "activer")),
                e -> {
                    r.enabled = !r.enabled;
                    mgr.saveRankings();
                    openEdit(p, r);
                });

        g.set(15, Txt.item(Material.GOLD_INGOT, "<gold>Récompenses Solo", "<gray>Commandes données aux meilleurs joueurs", "", "<yellow>Clique pour modifier"),
                e -> openRewards(p, r, false));
        g.set(16, Txt.item(Material.RED_BANNER, "<red>Récompenses Ville", "<gray>Commandes données à chaque membre", "<gray>ayant contribué pour la ville gagnante", "", "<yellow>Clique pour modifier"),
                e -> openRewards(p, r, true));

        g.set(18, back("Retour"), e -> openAdmin(p));
        g.set(22, Txt.item(Material.BARRIER, "<red><bold>Supprimer ce classement", "<red>Shift + clic pour confirmer"),
                e -> {
                    if (e.getClick() != ClickType.SHIFT_LEFT && e.getClick() != ClickType.SHIFT_RIGHT) {
                        p.sendMessage(Txt.c("<red>Fais Shift + clic pour confirmer."));
                        return;
                    }
                    mgr.remove(r);
                    openAdmin(p);
                });
        g.open(p);
    }

    // ------------------------------------------------------------------ admin : récompenses

    public void openRewards(Player p, Ranking r, boolean town) {
        Gui g = new Gui(27, "Récompenses " + (town ? "Ville" : "Solo") + " - " + r.name);
        g.set(4, Txt.item(Material.OAK_SIGN, "<gold>Récompenses " + (town ? "Ville" : "Solo"),
                "<gray>Commandes exécutées par la console", "<gray>à la fin de la semaine.",
                "<gray>Placeholders : <white>%player%</white>, <white>%rank%</white>, <white>%classement%</white>"));
        for (int rank = 1; rank <= RankingManager.MAX_RANKS; rank++) {
            final int rk = rank;
            List<String> cmds = r.rewards(town).getOrDefault(rank, List.of());
            List<String> lore = new ArrayList<>();
            if (cmds.isEmpty()) lore.add("<dark_gray>Aucune commande");
            for (String c : cmds) lore.add("<white>/" + c.replace("<", "\\<"));
            lore.add("");
            lore.add("<yellow>Clic gauche : ajouter une commande");
            lore.add("<yellow>Clic droit : retirer la dernière");
            lore.add("<yellow>Shift + clic droit : tout effacer");
            Material m = rank == 1 ? Material.GOLD_INGOT : rank == 2 ? Material.IRON_INGOT : rank == 3 ? Material.COPPER_INGOT : Material.PAPER;
            g.set(8 + rank, Txt.item(m, rankColor(rank) + "<bold>Rang #" + rank, lore), e -> {
                ClickType ct = e.getClick();
                if (ct == ClickType.SHIFT_RIGHT) {
                    r.rewards(town).remove(rk);
                    mgr.saveRankings();
                    openRewards(p, r, town);
                } else if (ct.isRightClick()) {
                    List<String> l = r.rewards(town).get(rk);
                    if (l != null && !l.isEmpty()) l.remove(l.size() - 1);
                    mgr.saveRankings();
                    openRewards(p, r, town);
                } else {
                    prompt(p, "Écris la commande (sans /) pour le rang #" + rk + ", ex : eco give %player% 1000", s -> {
                        String cmd = s.startsWith("/") ? s.substring(1) : s;
                        r.rewardList(town, rk).add(cmd);
                        mgr.saveRankings();
                        openRewards(p, r, town);
                    });
                }
            });
        }
        g.set(22, back("Retour"), e -> openEdit(p, r));
        g.open(p);
    }
}
