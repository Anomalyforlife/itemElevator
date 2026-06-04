package it.anomalyforlife.itemelevators.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
import it.anomalyforlife.itemelevators.upgrade.UpgradeConfig;
import it.anomalyforlife.itemelevators.upgrade.UpgradeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Controller for the physical chest inventory of a single elevator chest.
 * Slot 26 is reserved for the upgrade button while the GUI is active.
 */
public class ElevatorGUI {

    public static final int UPGRADE_SLOT = 26;

    private final ItemElevators plugin;
    private final Elevator elevator;
    private final Location chestLocation;
    private Inventory inventory;
    private ItemStack savedUpgradeSlot;
    private boolean active;

    public ElevatorGUI(ItemElevators plugin, Elevator elevator, Location chestLocation) {
        this.plugin = plugin;
        this.elevator = elevator;
        this.chestLocation = chestLocation;
        refreshInventory();
    }

    private boolean refreshInventory() {
        if (!(chestLocation.getBlock().getState() instanceof Chest chest)) {
            inventory = null;
            return false;
        }

        inventory = chest.getInventory();
        return true;
    }

    private boolean ensureActive() {
        if (!refreshInventory()) return false;

        if (!active) {
            savedUpgradeSlot = inventory.getItem(UPGRADE_SLOT);
            active = true;
        }

        inventory.setItem(UPGRADE_SLOT, buildUpgradeButton());
        return true;
    }

    private void restoreUpgradeSlot() {
        if (!refreshInventory()) return;

        inventory.setItem(UPGRADE_SLOT, savedUpgradeSlot);
        savedUpgradeSlot = null;
        active = false;
    }

    public void refreshUpgradeButton() {
        if (!active || !refreshInventory()) return;
        inventory.setItem(UPGRADE_SLOT, buildUpgradeButton());
    }

    private ItemStack buildUpgradeButton() {
        UpgradeService svc = plugin.getUpgradeService();
        UpgradeConfig cfg = svc.getConfig();
        int level = svc.getLevel(elevator);
        int maxLevel = cfg.getMaxLevel();
        boolean isMax = level >= maxLevel;

        Material mat = isMax ? Material.NETHER_STAR : Material.EXPERIENCE_BOTTLE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Component name = isMax
                ? Component.text("✦ MAX LEVEL ✦", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
                : Component.text("Upgrade  ", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Lv " + level + " → " + (level + 1), NamedTextColor.WHITE));
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        UpgradeConfig.LevelData current = cfg.getLevelData(level);
        lore.add(Component.empty());
        lore.add(line(NamedTextColor.GRAY, "Items/transfer: ",
                NamedTextColor.YELLOW, String.valueOf(current.itemsPerTransfer())));

        if (!isMax) {
            UpgradeConfig.LevelData next = cfg.getLevelData(level + 1);
            List<Elevator> chain = plugin.getElevatorManager().getChain(elevator);
            double totalCost = plugin.getUpgradeService().getChainUpgradeCost(chain);
            lore.add(Component.empty());
            lore.add(line(NamedTextColor.GRAY, "After upgrade: ",
                    NamedTextColor.GREEN, next.itemsPerTransfer() + " items/transfer"));
            lore.add(line(NamedTextColor.GRAY, "Cost: ",
                NamedTextColor.AQUA, formatCost(totalCost)));
            lore.add(Component.empty());
            lore.add(Component.text("► Click to upgrade", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("This elevator is fully upgraded!", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        plugin.getElevatorItem().markUpgradeButton(item);
        return item;
    }

    public boolean isControlSlot(int slot) {
        return slot == UPGRADE_SLOT;
    }

    private Component line(NamedTextColor labelColor, String label,
                           NamedTextColor valueColor, String value) {
        return Component.text(label, labelColor)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value, valueColor)
                        .decoration(TextDecoration.ITALIC, false));
    }

    private String formatCost(double cost) {
        if (cost <= 0) return "Free";
        if (plugin.hasEconomy()) return plugin.getEconomy().format(cost);
        long rounded = Math.round(cost);
        return String.valueOf(rounded);
    }

    public void open(Player player) {
        if (ensureActive()) {
            player.openInventory(inventory);
        }
    }

    public void closeAll() {
        if (inventory == null) return;
        for (HumanEntity viewer : List.copyOf(inventory.getViewers())) {
            viewer.closeInventory();
        }
    }

    public boolean hasViewers() {
        return inventory != null && !inventory.getViewers().isEmpty();
    }

    public void closeIfInactive() {
        if (!hasViewers()) {
            restoreUpgradeSlot();
        }
    }

    public Elevator getElevator() {
        return elevator;
    }

    public Location getChestLocation() {
        return chestLocation;
    }

    public Inventory getInventory() {
        return inventory;
    }
}
