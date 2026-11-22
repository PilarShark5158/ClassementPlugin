package fr.classement;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class StatListener implements Listener {
    private final ClassementPlugin plugin;

    public StatListener(ClassementPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.manager().rememberName(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        plugin.manager().add(killer, StatType.KILL_MOB, e.getEntityType().getKey().getKey(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        Player p = e.getPlayer();
        if (Tag.LOGS.isTagged(m)) {
            plugin.manager().add(p, StatType.WOOD, null, 1);
            return;
        }
        if (isHarvestable(b, m)) plugin.manager().add(p, StatType.CROP, null, 1);
    }

    private boolean isHarvestable(Block b, Material m) {
        if (m == Material.PUMPKIN || m == Material.MELON) return true;
        if (Tag.CROPS.isTagged(m) || m == Material.NETHER_WART) {
            if (b.getBlockData() instanceof Ageable a) return a.getAge() >= a.getMaximumAge();
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Entity caught = e.getCaught();
        if (caught == null) return;
        plugin.manager().add(e.getPlayer(), StatType.FISH, null, 1);
    }
}
