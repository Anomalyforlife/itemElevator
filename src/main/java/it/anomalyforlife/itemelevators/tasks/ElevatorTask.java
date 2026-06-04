package it.anomalyforlife.itemelevators.tasks;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
import it.anomalyforlife.itemelevators.elevator.ElevatorManager;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Transfers items from each elevator's bottom chest to its top chest.
 *
 * Lag-proof design:
 *  - Sync on main thread (Bukkit inventory API requirement).
 *  - Skips elevators whose GUI is open (player is actively viewing).
 *  - Skips elevators in unloaded chunks.
 *  - Short-circuits on isEmpty() before touching item arrays.
 *  - Respects the per-elevator itemsPerTransfer limit from UpgradeService.
 *  - Collects broken elevators and removes them after the loop (no CME).
 */
public class ElevatorTask extends BukkitRunnable {

    private final ItemElevators plugin;

    public ElevatorTask(ItemElevators plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ElevatorManager manager = plugin.getElevatorManager();
        List<Elevator> snapshot = new ArrayList<>(manager.getAllElevators());
        List<Elevator> toRemove = new ArrayList<>();

        for (Elevator elevator : snapshot) {
            if (manager.hasOpenGUI(elevator)) continue;
            if (!elevator.isChunksLoaded()) continue;

            if (!processElevator(elevator)) {
                toRemove.add(elevator);
            }
        }

        if (!toRemove.isEmpty()) {
            for (Elevator bad : toRemove) manager.unregisterElevator(bad);
            manager.saveData();
        }
    }

    /**
     * Moves up to {@code itemsPerTransfer} items from bottom to top chest.
     *
     * @return false if the elevator blocks are gone and it should be unregistered.
     */
    private boolean processElevator(Elevator elevator) {
        Chest bottomChest;
        Chest topChest;
        try {
            var bs = elevator.getBottomChest().getBlock().getState();
            var ts = elevator.getTopChest().getBlock().getState();
            if (!(bs instanceof Chest) || !(ts instanceof Chest)) return false;
            bottomChest = (Chest) bs;
            topChest    = (Chest) ts;
        } catch (Exception e) {
            return false;
        }

        Inventory src = bottomChest.getInventory();
        Inventory dst = topChest.getInventory();

        if (src.isEmpty()) return true;
        if (dst.firstEmpty() == -1 && !hasPartialStack(dst)) return true;

        int limit = plugin.getUpgradeService().getItemsPerTransfer(elevator);
        int moved = 0;

        ItemStack[] contents = src.getContents();
        for (int i = 0; i < contents.length && moved < limit; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;

            // How many we're allowed to move in this cycle
            int canMove = Math.min(item.getAmount(), limit - moved);
            ItemStack toMove = item.clone();
            toMove.setAmount(canMove);

            Map<Integer, ItemStack> leftover = dst.addItem(toMove);

            int actuallyMoved = canMove;
            if (!leftover.isEmpty()) {
                actuallyMoved = canMove - leftover.values().iterator().next().getAmount();
            }

            if (actuallyMoved <= 0) continue;

            moved += actuallyMoved;

            int remaining = item.getAmount() - actuallyMoved;
            if (remaining <= 0) {
                src.setItem(i, null);
            } else {
                ItemStack leftoverItem = item.clone();
                leftoverItem.setAmount(remaining);
                src.setItem(i, leftoverItem);
            }
        }

        return true;
    }

    private boolean hasPartialStack(Inventory inv) {
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }
}
