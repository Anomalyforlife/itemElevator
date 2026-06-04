package it.anomalyforlife.itemelevators.listeners;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
import it.anomalyforlife.itemelevators.gui.ElevatorGUI;
import it.anomalyforlife.itemelevators.upgrade.UpgradeService.UpgradeResult;

public class InventoryListener implements Listener {

    private final ItemElevators plugin;

    public InventoryListener(ItemElevators plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Location location = event.getView().getTopInventory().getLocation();
        if (location == null) return;

        ElevatorGUI gui = plugin.getElevatorManager().getGUI(location);
        if (gui == null) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            gui.closeIfInactive();
            if (!gui.hasViewers()) {
                plugin.getElevatorManager().markGUIClosed(gui.getChestLocation());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Location location = event.getView().getTopInventory().getLocation();
        if (location == null) return;

        ElevatorGUI gui = plugin.getElevatorManager().getGUI(location);
        if (gui == null) return;

        if (event.getRawSlot() == ElevatorGUI.UPGRADE_SLOT) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleUpgrade(player, gui);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Location location = event.getView().getTopInventory().getLocation();
        if (location == null) return;

        if (plugin.getElevatorManager().getGUI(location) == null) return;

        if (event.getRawSlots().contains(ElevatorGUI.UPGRADE_SLOT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ElevatorGUI sourceGui = getGui(event.getSource().getLocation());
        ElevatorGUI destinationGui = getGui(event.getDestination().getLocation());

        if (sourceGui == null && destinationGui == null) return;

        if (plugin.getElevatorItem().isUpgradeButton(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private ElevatorGUI getGui(Location location) {
        if (location == null) return null;
        return plugin.getElevatorManager().getGUI(location);
    }

    private void handleUpgrade(Player player, ElevatorGUI gui) {
        List<Elevator> chain = plugin.getElevatorManager().getChain(gui.getElevator());
        UpgradeResult result = plugin.getUpgradeService().tryUpgradeChain(player, chain);

        switch (result) {
            case SUCCESS -> {
                int chestLevel = plugin.getElevatorManager().getMaxLevelAt(gui.getChestLocation());
                for (Elevator elevator : chain) {
                    plugin.getElevatorItem().markBlock(elevator.getBottomChest().getBlock(), chestLevel);
                    plugin.getElevatorItem().markBlock(elevator.getTopChest().getBlock(), chestLevel);
                }
                plugin.getElevatorManager().saveData();
                gui.refreshUpgradeButton();
                int items = plugin.getUpgradeService().getConfig().getLevelData(chestLevel).itemsPerTransfer();
                plugin.getLangManager().send(player, "upgrade.success",
                    "{level}", String.valueOf(chestLevel),
                    "{items}", String.valueOf(items),
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
