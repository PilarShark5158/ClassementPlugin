package fr.classement;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Gui implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public Gui(int size, String title) {
        this.inv = Bukkit.createInventory(this, size, Txt.c(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void set(int slot, ItemStack item) {
        inv.setItem(slot, item);
    }

    public void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        actions.put(slot, action);
    }

    public Consumer<InventoryClickEvent> action(int slot) {
        return actions.get(slot);
    }

    public void open(Player p) {
        p.openInventory(inv);
    }
}
