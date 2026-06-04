package it.anomalyforlife.itemelevators.gui;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
import it.anomalyforlife.itemelevators.upgrade.UpgradeConfig;
import it.anomalyforlife.itemelevators.upgrade.UpgradeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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
 * 54-slot linked GUI for an elevator pair.
 *
 * Slot layout:
 *   0  – 26  → top chest slots 0-26    (rows 1-3)
 *   27 – 52  → bottom chest slots 0-25 (rows 4-6, first 26 slots)
 *   53       → upgrade button          (bottom-right corner, not synced)
 *
 * Slot 4  = decorative top-chest label (chest slot 4 inaccessible from GUI).
 * Slot 31 = decorative bottom-chest label (bottom chest slot 4 inaccessible).
 */
public class ElevatorGUI implements InventoryHolder {

    public static final int UPGRADE_SLOT  = 53;
    public static final int LABEL_TOP     = 4;
    public static final int LABEL_BOTTOM  = 31;

    private final ItemElevators plugin;
    private final Elevator elevator;
    private final Inventory inventory;

    public ElevatorGUI(ItemElevators plugin, Elevator elevator) {
        this.plugin = plugin;
        this.elevator = elevator;
        this.inventory = Bukkit.createInventory(this, 54, plugin.getLangManager().get("gui.title"));
        populateStaticItems();
        syncFromChests();
    }

    // -------------------------------------------------------------------------
    // Sync
    // -------------------------------------------------------------------------

    /** Copy physical chest contents into the virtual inventory. */
    public void syncFromChests() {
        loadChest(elevator.getTopChest().getBlock(), 0);
        loadChest(elevator.getBottomChest().getBlock(), 27);
        refreshUpgradeButton();
    }

    /** Flush the virtual inventory back to the physical chests. */
    public void syncToChests() {
        saveChest(elevator.getTopChest().getBlock(), 0);
        saveChest(elevator.getBottomChest().getBlock(), 27);
    }

    private void loadChest(org.bukkit.block.Block block, int offset) {
        if (!(block.getState() instanceof Chest chest)) return;
        ItemStack[] contents = chest.getInventory().getContents();
        int slots = Math.min(27, contents.length);
        for (int i = 0; i < slots; i++) {
            int guiSlot = offset + i;
            if (isControlSlot(guiSlot)) continue;
            inventory.setItem(guiSlot, contents[i]);
        }
    }

    private void saveChest(org.bukkit.block.Block block, int offset) {
        if (!(block.getState() instanceof Chest chest)) return;
        // Top chest: gui 0-26 → chest 0-26
        // Bottom chest: gui 27-52 → chest 0-25 (slot 53 is the button, not saved)
        int maxGuiSlot = (offset == 0) ? 26 : 52;
        for (int guiSlot = offset; guiSlot <= maxGuiSlot; guiSlot++) {
            if (isControlSlot(guiSlot)) continue;
            int chestSlot = guiSlot - offset;
            chest.getInventory().setItem(chestSlot, inventory.getItem(guiSlot));
        }
    }

    // -------------------------------------------------------------------------
    // Static decorative items
    // -------------------------------------------------------------------------

    private void populateStaticItems() {
        inventory.setItem(LABEL_TOP, makeLabel(Material.LIME_STAINED_GLASS_PANE,
                plugin.getLangManager().get("gui.top-label")));
        inventory.setItem(LABEL_BOTTOM, makeLabel(Material.RED_STAINED_GLASS_PANE,
                plugin.getLangManager().get("gui.bottom-label")));
    }

    // -------------------------------------------------------------------------
    // Upgrade button
    // -------------------------------------------------------------------------

    public void refreshUpgradeButton() {
        inventory.setItem(UPGRADE_SLOT, buildUpgradeButton());
    }

    private ItemStack buildUpgradeButton() {
        UpgradeService svc = plugin.getUpgradeService();
        UpgradeConfig cfg = svc.getConfig();
        int level = svc.getLevel(elevator);
        int maxLevel = cfg.getMaxLevel();
        boolean isMax = level >= maxLevel;

        UpgradeConfig.LevelData current = cfg.getLevelData(level);

        Material mat = isMax ? Material.NETHER_STAR : Material.EXPERIENCE_BOTTLE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Title
        Component name;
        if (isMax) {
            name = Component.text("✦ MAX LEVEL ✦", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true);
        } else {
            name = Component.text("Upgrade  ", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, false)
                    .append(Component.text("Lv " + level + " → " + (level + 1), NamedTextColor.WHITE));
        }
        meta.displayName(name);

        // Lore
        List<Component> lore = new ArrayList<>();
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
    // Helpers
    // -------------------------------------------------------------------------

    public boolean isControlSlot(int slot) {
        return slot == LABEL_TOP || slot == LABEL_BOTTOM || slot == UPGRADE_SLOT;
    }

    private ItemStack makeLabel(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of());
            item.setItemMeta(meta);
        }
        return item;
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

    public Elevator getElevator() { return elevator; }

    @Override
    public Inventory getInventory() { return inventory; }
}
