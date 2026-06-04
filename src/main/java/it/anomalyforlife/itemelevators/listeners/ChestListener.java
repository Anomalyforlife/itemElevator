package it.anomalyforlife.itemelevators.listeners;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
import it.anomalyforlife.itemelevators.elevator.ElevatorManager;
import it.anomalyforlife.itemelevators.gui.ElevatorGUI;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public class ChestListener implements Listener {

    private final ItemElevators plugin;

    public ChestListener(ItemElevators plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Chest right-click — detect or open elevator GUI
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestClick(PlayerInteractEvent event) {
        // Only main-hand, right-click on a block
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        if (block.getType() != Material.CHEST) return;

        Player player = event.getPlayer();
        ElevatorManager manager = plugin.getElevatorManager();

        Optional<Elevator> elevatorOpt = manager.getElevatorAt(block.getLocation());

        if (elevatorOpt.isEmpty()) {
            // Try to detect a new elevator
            elevatorOpt = manager.detectElevator(block.getLocation());

            if (elevatorOpt.isPresent()) {
                Elevator elevator = elevatorOpt.get();

                // Permission check
                if (!player.hasPermission("itemelevators.create")) {
                    plugin.getLangManager().send(player, "elevator.no-permission");
                    return;
                }

                // Economy check
                if (plugin.getConfigManager().isEconomyEnabled()
                        && plugin.hasEconomy()
                        && !player.hasPermission("itemelevators.bypass-cost")) {

                    double cost = plugin.getConfigManager().getCreationCost();
                    if (cost > 0) {
                        if (!plugin.getEconomy().has(player, cost)) {
                            plugin.getLangManager().send(player, "economy.not-enough",
                                    "{cost}", plugin.getEconomy().format(cost));
                            event.setCancelled(true);
                            return;
                        }
                        plugin.getEconomy().withdrawPlayer(player, cost);
                        plugin.getLangManager().send(player, "economy.charged",
                                "{cost}", plugin.getEconomy().format(cost));
                    } else if (player.hasPermission("itemelevators.bypass-cost")) {
                        plugin.getLangManager().send(player, "economy.bypass");
                    }
                }

                manager.registerElevator(elevator);
                plugin.getUpgradeService().registerElevator(elevator);
                manager.saveData();

                plugin.getLangManager().send(player, "elevator.created",
                        "{interval}", String.valueOf(plugin.getConfigManager().getTransferInterval()),
                        "{items}", String.valueOf(plugin.getUpgradeService().getItemsPerTransfer(elevator)));
            }
        }

        // Open GUI if this chest is now part of an elevator
        if (elevatorOpt.isPresent()) {
            event.setCancelled(true);
            Elevator elevator = elevatorOpt.get();

            // Validate that physical blocks are still intact
            if (!isValidElevatorState(elevator)) {
                manager.unregisterElevator(elevator);
                manager.saveData();
                plugin.getLangManager().send(player, "elevator.invalid");
                return;
            }

            ElevatorGUI gui = manager.getOrCreateGUI(elevator);
            // Refresh contents before opening for this player
            if (!gui.hasViewers()) {
                gui.syncFromChests();
            }
            gui.open(player);
        }
    }

    // -------------------------------------------------------------------------
    // Block break — invalidate elevator if a chest or conductor is removed
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ElevatorManager manager = plugin.getElevatorManager();
        Material conductor = plugin.getConfigManager().getConductorMaterial();

        if (block.getType() == Material.CHEST) {
            if (manager.isElevatorChest(block.getLocation())) {
                manager.invalidateAt(block.getLocation());
                manager.saveData();
                Player player = event.getPlayer();
                plugin.getLangManager().send(player, "elevator.removed");
            }
            return;
        }

        // Conductor block removed — check if it was part of any elevator column
        if (block.getType() == conductor) {
            invalidateElevatorInColumn(block, manager);
        }
    }

    // -------------------------------------------------------------------------
    // Block place — no-op here; detection is lazy (on interact)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // A newly placed block inside an existing elevator column breaks it
        Block block = event.getBlock();
        if (block.getType() == Material.CHEST) return; // Handled on interact
        ElevatorManager manager = plugin.getElevatorManager();
        invalidateElevatorInColumn(block, manager);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void invalidateElevatorInColumn(Block changedBlock, ElevatorManager manager) {
        int maxDist = plugin.getConfigManager().getMaxDistance();

        // Scan up and down from the changed block looking for registered elevator chests
        for (int dir : new int[]{1, -1}) {
            for (int dist = 1; dist <= maxDist + 1; dist++) {
                Block candidate = changedBlock.getRelative(0, dist * dir, 0);
                if (candidate.getType() == Material.CHEST && manager.isElevatorChest(candidate.getLocation())) {
                    manager.invalidateAt(candidate.getLocation());
                    manager.saveData();
                    return;
                }
                // Stop scanning if we hit something that cannot be part of the column
                Material m = candidate.getType();
                if (m != Material.CHEST && m != plugin.getConfigManager().getConductorMaterial() && m != Material.AIR) {
                    break;
                }
            }
        }
    }

    private boolean isValidElevatorState(Elevator elevator) {
        Block bottom = elevator.getBottomChest().getBlock();
        Block top = elevator.getTopChest().getBlock();
        if (bottom.getType() != Material.CHEST || top.getType() != Material.CHEST) return false;

        Material conductor = plugin.getConfigManager().getConductorMaterial();
        int dy = top.getY() - bottom.getY();
        for (int i = 1; i < dy; i++) {
            if (bottom.getRelative(0, i, 0).getType() != conductor) return false;
        }
        return true;
    }
}
