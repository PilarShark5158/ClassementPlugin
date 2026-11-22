package fr.classement;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/** Suivi de l'argent gagné via Vault (réflexion) : on ajoute chaque hausse de solde. */
public class EconomyBridge {
    private final ClassementPlugin plugin;
    private Object eco;
    private Method getBalance;

    public EconomyBridge(ClassementPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean hook() {
        if (eco != null) return true;
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return false;
        try {
            Class<?> ec = vault.getClass().getClassLoader().loadClass("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(ec);
            if (rsp == null) return false;
            eco = rsp.getProvider();
            getBalance = ec.getMethod("getBalance", OfflinePlayer.class);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    public void poll() {
        if (!hook()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                long bal = (long) Math.floor(((Number) getBalance.invoke(eco, p)).doubleValue());
                Long last = plugin.manager().lastBalance(p.getUniqueId());
                if (last != null && bal > last) plugin.manager().add(p, StatType.MONEY, null, bal - last);
                plugin.manager().setLastBalance(p.getUniqueId(), bal);
            } catch (Throwable ignored) {
            }
        }
    }
}
