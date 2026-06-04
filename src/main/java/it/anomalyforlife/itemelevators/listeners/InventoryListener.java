package it.anomalyforlife.itemelevators.listeners;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.gui.ElevatorGUI;
import it.anomalyforlife.itemelevators.upgrade.UpgradeService.UpgradeResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class InventoryListener implements Listener {

    private final ItemElevators plugin;

    public InventoryListener(ItemElevators plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Close — flush changes back to physical chests
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ElevatorGUI gui)) return;

        // 1-tick delay: viewer is removed from the list after the event fires
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!gui.hasViewers()) {
                gui.syncToChests();
                plugin.getElevatorManager().markGUIClosed(gui.getElevator());
            }
        }, 1L);
    }

    // -------------------------------------------------------------------------
    // Click — handle upgrade button and block control slots
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ElevatorGUI gui)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (!gui.isControlSlot(slot)) return;

        // Always cancel clicks on control slots (labels + upgrade button)
        event.setCancelled(true);

        // Upgrade button clicked
        if (slot == ElevatorGUI.UPGRADE_SLOT && event.getWhoClicked() instanceof Player player) {
            handleUpgrade(player, gui);
        }
    }

    // -------------------------------------------------------------------------
    // Drag — prevent dragging onto control slots
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ElevatorGUI gui)) return;

        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < event.getInventory().getSize() && gui.isControlSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Upgrade logic
    // -------------------------------------------------------------------------

    private void handleUpgrade(Player player, ElevatorGUI gui) {
        UpgradeResult result = plugin.getUpgradeService().tryUpgrade(player, gui.getElevator());

        switch (result) {
            case SUCCESS -> {
                plugin.getElevatorManager().saveData();
                gui.refreshUpgradeButton();
                plugin.getLangManager().send(player, "upgrade.success",
                        "{level}", String.valueOf(plugin.getUpgradeService().getLevel(gui.getElevator())),
                        "{items}", String.valueOf(plugin.getUpgradeService().getItemsPerTransfer(gui.getElevator())));
            }
            case MAX_LEVEL ->
                    plugin.getLangManager().send(player, "upgrade.max-level");
            case NOT_ENOUGH_MONEY -> {
                int nextLevel = plugin.getUpgradeService().getLevel(gui.getElevator()) + 1;
                int cost = plugin.getUpgradeService().getConfig().getLevelData(nextLevel).cost();
                String formatted = plugin.hasEconomy()
                        ? plugin.getEconomy().format(cost) : String.valueOf(cost);
                plugin.getLangManager().send(player, "upgrade.not-enough-money",
                        "{cost}", formatted);
            }
            case VAULT_NOT_AVAILABLE ->
                    plugin.getLangManager().send(player, "upgrade.vault-unavailable");
            case NOT_ENABLED ->
                    plugin.getLangManager().send(player, "upgrade.not-enabled");
            case DB_ERROR ->
                    plugin.getLangManager().send(player, "upgrade.error");
        }
    }
}
