package it.anomalyforlife.itemelevators.listeners;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
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

import java.util.List;

public class InventoryListener implements Listener {

    private final ItemElevators plugin;

    public InventoryListener(ItemElevators plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Close — GUI is read-only, no sync back to chests needed
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ElevatorGUI gui)) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!gui.hasViewers()) {
                plugin.getElevatorManager().markGUIClosed(gui.getChestLocation());
            }
        }, 1L);
    }

    // -------------------------------------------------------------------------
    // Click — GUI is read-only; only the upgrade button does something
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ElevatorGUI gui)) return;

        // Block all interaction — the GUI is view-only
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == ElevatorGUI.UPGRADE_SLOT && event.getWhoClicked() instanceof Player player) {
            handleUpgrade(player, gui);
        }
    }

    // -------------------------------------------------------------------------
    // Drag — block all drags
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ElevatorGUI)) return;
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Upgrade logic
    // -------------------------------------------------------------------------

    private void handleUpgrade(Player player, ElevatorGUI gui) {
        List<Elevator> chain = plugin.getElevatorManager().getChain(gui.getElevator());
        UpgradeResult result = plugin.getUpgradeService().tryUpgradeChain(player, chain);

        switch (result) {
            case SUCCESS -> {
                plugin.getElevatorManager().saveData();
                gui.refreshUpgradeButton();
                Elevator elevator = gui.getElevator();
                plugin.getLangManager().send(player, "upgrade.success",
                        "{level}", String.valueOf(plugin.getUpgradeService().getLevel(elevator)),
                        "{items}", String.valueOf(plugin.getUpgradeService().getItemsPerTransfer(elevator)),
                        "{chain}", String.valueOf(chain.size()));
            }
            case MAX_LEVEL ->
                    plugin.getLangManager().send(player, "upgrade.max-level");
            case NOT_ENOUGH_MONEY -> {
                double totalCost = plugin.getUpgradeService().getChainUpgradeCost(chain);
                String formatted = plugin.hasEconomy()
                        ? plugin.getEconomy().format(totalCost) : String.valueOf((long) totalCost);
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
