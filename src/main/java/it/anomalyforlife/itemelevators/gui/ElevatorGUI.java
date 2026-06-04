package it.anomalyforlife.itemelevators.gui;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
import it.anomalyforlife.itemelevators.upgrade.UpgradeConfig;
import it.anomalyforlife.itemelevators.upgrade.UpgradeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 36-slot read-only view of a single elevator chest.
 *
 * Slot layout:
 *   0  – 26  → chest contents (display only, items cannot be taken)
 *   27 – 34  → filler (glass pane)
 *   35       → upgrade button
 *
 * Players who want to manage items directly should sneak + right-click to
 * open the physical chest instead.
 */
public class ElevatorGUI implements InventoryHolder {

    public static final int UPGRADE_SLOT = 35;

    private static final int CONTROL_ROW_START = 27;

    private final ItemElevators plugin;
    private final Elevator elevator;
    private final Location chestLocation;
    private final Inventory inventory;

    public ElevatorGUI(ItemElevators plugin, Elevator elevator, Location chestLocation) {
        this.plugin        = plugin;
        this.elevator      = elevator;
        this.chestLocation = chestLocation;

        boolean isTop = chestLocation.getBlockY() == elevator.getTopChest().getBlockY();
        Component title = plugin.getLangManager().get(isTop ? "gui.title-top" : "gui.title-bottom");

        this.inventory = Bukkit.createInventory(this, 36, title);
        fillControlRow();
        syncFromChest();
    }

    // -------------------------------------------------------------------------
    // Sync — one-way: physical chest → GUI (GUI is read-only, never writes back)
    // -------------------------------------------------------------------------

    public void syncFromChest() {
        org.bukkit.block.Block block = chestLocation.getBlock();
        if (!(block.getState() instanceof Chest chest)) return;

        ItemStack[] contents = chest.getInventory().getContents();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, contents[i]);
        }
        refreshUpgradeButton();
    }

    // -------------------------------------------------------------------------
    // Control row
    // -------------------------------------------------------------------------

    private void fillControlRow() {
        ItemStack filler = makeFiller();
        for (int slot = CONTROL_ROW_START; slot < 36; slot++) {
            inventory.setItem(slot, filler);
        }
        refreshUpgradeButton();
    }

    public void refreshUpgradeButton() {
        inventory.setItem(UPGRADE_SLOT, buildUpgradeButton());
    }

    private ItemStack buildUpgradeButton() {
        UpgradeService svc = plugin.getUpgradeService();
        UpgradeConfig cfg  = svc.getConfig();
        int level    = svc.getLevel(elevator);
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
            lore.add(Component.empty());
            lore.add(line(NamedTextColor.GRAY, "After upgrade: ",
                    NamedTextColor.GREEN, next.itemsPerTransfer() + " items/transfer"));
            lore.add(line(NamedTextColor.GRAY, "Cost: ",
                    NamedTextColor.AQUA, formatCost(next.cost(), svc)));
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
        return item;
    }

    private ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public boolean isControlSlot(int slot) {
        return slot >= CONTROL_ROW_START;
    }

    private Component line(NamedTextColor labelColor, String label,
                           NamedTextColor valueColor, String value) {
        return Component.text(label, labelColor)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value, valueColor)
                        .decoration(TextDecoration.ITALIC, false));
    }

    private String formatCost(int cost, UpgradeService svc) {
        if (cost <= 0) return "Free";
        if (plugin.hasEconomy()) return plugin.getEconomy().format(cost);
        return String.valueOf(cost);
    }

    // -------------------------------------------------------------------------
    // Viewer management
    // -------------------------------------------------------------------------

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void closeAll() {
        for (HumanEntity viewer : List.copyOf(inventory.getViewers())) {
            viewer.closeInventory();
        }
    }

    public boolean hasViewers() {
        return !inventory.getViewers().isEmpty();
    }

    public Elevator getElevator()          { return elevator; }
    public Location getChestLocation()     { return chestLocation; }

    @Override
    public Inventory getInventory()        { return inventory; }
}
