package it.anomalyforlife.itemelevators.upgrade;

import it.anomalyforlife.itemelevators.elevator.Elevator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.HashMap;
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
