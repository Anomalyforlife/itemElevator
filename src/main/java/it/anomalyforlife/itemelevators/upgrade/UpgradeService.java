package it.anomalyforlife.itemelevators.upgrade;

import it.anomalyforlife.itemelevators.elevator.Elevator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UpgradeService {

    private volatile UpgradeConfig config;
    private final Economy economy; // null when Vault absent or upgrades disabled

    /** Persisted level for each elevator. Default level = 1. */
    private final Map<Elevator, Integer> levelCache = new HashMap<>();

    public UpgradeService(UpgradeConfig config, Economy economy) {
        this.config = config;
        this.economy = economy;
    }

    public void updateConfig(UpgradeConfig config) {
        this.config = config;
    }

    public UpgradeConfig getConfig() { return config; }

    // -------------------------------------------------------------------------
    // Level access
    // -------------------------------------------------------------------------

    public int getLevel(Elevator elevator) {
        return levelCache.getOrDefault(elevator, 1);
    }

    public void setLevel(Elevator elevator, int level) {
        levelCache.put(elevator, Math.max(1, Math.min(level, config.getMaxLevel())));
    }

    public UpgradeConfig.LevelData getLevelData(Elevator elevator) {
        return config.getLevelData(getLevel(elevator));
    }

    public int getItemsPerTransfer(Elevator elevator) {
        return getLevelData(elevator).itemsPerTransfer();
    }

    /** Called when a new elevator is registered (starts at level 1). */
    public void registerElevator(Elevator elevator) {
        levelCache.putIfAbsent(elevator, 1);
    }

    /** Called when an elevator is registered with a known level (loaded from disk). */
    public void registerElevator(Elevator elevator, int level) {
        levelCache.put(elevator, Math.max(1, Math.min(level, config.getMaxLevel())));
    }

    /** Called when an elevator is removed. */
    public void unregisterElevator(Elevator elevator) {
        levelCache.remove(elevator);
    }

    // -------------------------------------------------------------------------
    // Upgrade logic
    // -------------------------------------------------------------------------

    public UpgradeResult tryUpgrade(Player player, Elevator elevator) {
        if (!config.isEnabled()) return UpgradeResult.NOT_ENABLED;

        int current = getLevel(elevator);
        if (current >= config.getMaxLevel()) return UpgradeResult.MAX_LEVEL;

        int next = current + 1;
        double cost = config.getLevelData(next).cost();

        if (cost > 0 && config.isVaultRequired()) {
            if (economy == null) return UpgradeResult.VAULT_NOT_AVAILABLE;
            if (!economy.has(player, cost)) return UpgradeResult.NOT_ENOUGH_MONEY;
            economy.withdrawPlayer(player, cost);
        }

        levelCache.put(elevator, next);
        return UpgradeResult.SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Chain upgrade
    // -------------------------------------------------------------------------

    /**
     * Upgrades every elevator in the chain together. The cost is the per-level
     * unit cost multiplied by the number of elevators in the chain.
     */
    public UpgradeResult tryUpgradeChain(Player player, List<Elevator> chain) {
        if (!config.isEnabled()) return UpgradeResult.NOT_ENABLED;
        if (chain.isEmpty()) return UpgradeResult.DB_ERROR;

        // Use the minimum level in the chain so all reach the same next level
        int current = chain.stream().mapToInt(e -> levelCache.getOrDefault(e, 1)).min().orElse(1);
        if (current >= config.getMaxLevel()) return UpgradeResult.MAX_LEVEL;

        int next = current + 1;
        double totalCost = (double) config.getLevelData(next).cost() * chain.size();

        if (totalCost > 0 && config.isVaultRequired()) {
            if (economy == null) return UpgradeResult.VAULT_NOT_AVAILABLE;
            if (!economy.has(player, totalCost)) return UpgradeResult.NOT_ENOUGH_MONEY;
            economy.withdrawPlayer(player, totalCost);
        }

        for (Elevator e : chain) {
            levelCache.put(e, next);
        }
        return UpgradeResult.SUCCESS;
    }

    /** Returns the total upgrade cost for the next level across all elevators in the chain. */
    public double getChainUpgradeCost(List<Elevator> chain) {
        if (chain.isEmpty()) return 0;
        int current = chain.stream().mapToInt(e -> levelCache.getOrDefault(e, 1)).min().orElse(1);
        if (current >= config.getMaxLevel()) return 0;
        return (double) config.getLevelData(current + 1).cost() * chain.size();
    }

    // -------------------------------------------------------------------------
    // Snapshot (for persistence)
    // -------------------------------------------------------------------------

    /** Returns a copy of the level cache for serialisation. */
    public Map<Elevator, Integer> getLevelSnapshot() {
        return new HashMap<>(levelCache);
    }

    public enum UpgradeResult {
        SUCCESS, MAX_LEVEL, NOT_ENOUGH_MONEY, VAULT_NOT_AVAILABLE, NOT_ENABLED, DB_ERROR
    }
}
