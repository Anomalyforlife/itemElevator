package it.anomalyforlife.itemelevators.listeners;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
import it.anomalyforlife.itemelevators.elevator.ElevatorItem;
import it.anomalyforlife.itemelevators.elevator.ElevatorManager;
import it.anomalyforlife.itemelevators.gui.ElevatorGUI;

public class ChestListener implements Listener {

    private final ItemElevators plugin;

    public ChestListener(ItemElevators plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Block place — mark the placed block's TileState PDC if it came from a
    // special elevator chest item, so we can identify it later.
    // -------------------------------------------------------------------------

    private static final BlockFace[] HORIZONTALS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ElevatorItem elevatorItem = plugin.getElevatorItem();
        Block placed = event.getBlock();
        boolean placingElevator = elevatorItem.isElevatorItem(event.getItemInHand());

        // Prevent double-chest formation involving any elevator chest.
        // Only relevant when placing a chest (plain or elevator).
        if (placed.getType() == Material.CHEST) {
            for (BlockFace face : HORIZONTALS) {
                Block neighbour = placed.getRelative(face);
                if (neighbour.getType() != Material.CHEST) continue;

                if (placingElevator || elevatorItem.isElevatorBlock(neighbour)) {
                    event.setCancelled(true);
                    plugin.getLangManager().send(event.getPlayer(), "elevator.no-double-chest");
                    return;
                }
            }
        }

        if (!placingElevator) return;
        elevatorItem.markBlock(placed, elevatorItem.getItemLevel(event.getItemInHand()));
    }

    // -------------------------------------------------------------------------
    // Chest right-click — only elevator chests are intercepted; regular chests
    // open normally.
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        ElevatorItem elevatorItem = plugin.getElevatorItem();

        // Only intercept special elevator chests
        if (!elevatorItem.isElevatorBlock(block)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ElevatorManager manager = plugin.getElevatorManager();

        // If this chest is already the BOTTOM of an elevator, open its GUI.
        // If it's only a TOP (or unlinked), try to create a new elevator first.
        Optional<Elevator> bottomElevator = manager.getBottomElevatorAt(block.getLocation());

        if (bottomElevator.isEmpty()) {
            Optional<Elevator> detected = manager.detectElevator(block.getLocation());

            if (detected.isPresent()) {
                Elevator elevator = detected.get();

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
                            return;
                        }
                        plugin.getEconomy().withdrawPlayer(player, cost);
                        plugin.getLangManager().send(player, "economy.charged",
                                "{cost}", plugin.getEconomy().format(cost));
                    }
                }

                // Carry the level from the right-clicked (bottom) chest
                int level = elevatorItem.getBlockLevel(block);
                manager.registerElevator(elevator);
                plugin.getUpgradeService().registerElevator(elevator, level);
                manager.saveData();

                plugin.getLangManager().send(player, "elevator.created",
                        "{interval}", String.valueOf(plugin.getConfigManager().getTransferInterval()),
                        "{items}",    String.valueOf(plugin.getUpgradeService().getItemsPerTransfer(elevator)));

                bottomElevator = Optional.of(elevator);
            }
        }

        // Determine which elevator to show: prefer the one where this chest is
        // the bottom (outgoing), fall back to any elevator it belongs to.
        Optional<Elevator> guiTarget = bottomElevator.isPresent()
                ? bottomElevator
                : manager.getElevatorAt(block.getLocation());

        if (guiTarget.isEmpty()) {
            plugin.getLangManager().send(player, "elevator.no-partner");
            return;
        }

        Elevator elevator = guiTarget.get();

        if (!isValidElevatorState(elevator)) {
            manager.unregisterElevator(elevator);
            manager.saveData();
            plugin.getLangManager().send(player, "elevator.invalid");
            return;
        }

        ElevatorGUI gui = manager.getOrCreateGUI(elevator, block.getLocation());
        gui.open(player);
    }

    // -------------------------------------------------------------------------
    // Block break — intercept elevator chests to cancel the vanilla drop and
    // replace it with the special item carrying the upgrade level.
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;

        ElevatorItem elevatorItem = plugin.getElevatorItem();
        if (!elevatorItem.isElevatorBlock(block)) return;

        ElevatorManager manager = plugin.getElevatorManager();

        int level;
        if (manager.isElevatorChest(block.getLocation())) {
            level = manager.getMaxLevelAt(block.getLocation());
            manager.invalidateAt(block.getLocation());
            manager.saveData();
            plugin.getLangManager().send(event.getPlayer(), "elevator.removed");
        } else {
            level = elevatorItem.getBlockLevel(block);
        }

        // Drop the special item instead of a plain chest
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(), elevatorItem.create(level));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isValidElevatorState(Elevator elevator) {
        ElevatorItem elevatorItem = plugin.getElevatorItem();
        return elevatorItem.isElevatorBlock(elevator.getBottomChest().getBlock())
                && elevatorItem.isElevatorBlock(elevator.getTopChest().getBlock());
    }
}
