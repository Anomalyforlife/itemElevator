package it.anomalyforlife.itemelevators.upgrade;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UpgradeConfig {

    private final boolean enabled;
    private final boolean vaultRequired;
    private final int maxLevel;
    private final List<LevelData> levels; // index 0 = level 1

    public UpgradeConfig(boolean enabled, boolean vaultRequired, int maxLevel, List<LevelData> levels) {
        this.enabled = enabled;
        this.vaultRequired = vaultRequired;
        this.maxLevel = maxLevel;
        this.levels = Collections.unmodifiableList(new ArrayList<>(levels));
    }

    public boolean isEnabled() { return enabled; }
    public boolean isVaultRequired() { return vaultRequired; }
    public int getMaxLevel() { return maxLevel; }

    /** Returns data for the given level (1-based). Clamps to valid range. */
    public LevelData getLevelData(int level) {
        int idx = Math.max(0, Math.min(level - 1, levels.size() - 1));
        return levels.get(idx);
    }

    public static UpgradeConfig fromSection(ConfigurationSection section) {
        if (section == null) return defaults();

        boolean enabled = section.getBoolean("enabled", true);
        boolean vaultRequired = section.getBoolean("vault-required", true);
        int maxLevel = Math.max(1, section.getInt("max-level", 10));

        ConfigurationSection levelsSection = section.getConfigurationSection("levels");
        List<LevelData> levels = new ArrayList<>();
        if (levelsSection != null) {
            for (int lvl = 1; lvl <= maxLevel; lvl++) {
                ConfigurationSection s = levelsSection.getConfigurationSection(String.valueOf(lvl));
                if (s != null) {
                    levels.add(new LevelData(
                            s.getInt("cost", 0),
                            Math.max(1, Math.min(64, s.getInt("items-per-transfer", 1)))
                    ));
                } else {
                    levels.add(new LevelData(0, 1));
                }
            }
        }

        if (levels.isEmpty()) return defaults();
        return new UpgradeConfig(enabled, vaultRequired, maxLevel, levels);
    }

    public static UpgradeConfig defaults() {
        List<LevelData> lvls = new ArrayList<>();
        //          cost    items/tick
        lvls.add(new LevelData(     0,  1));
        lvls.add(new LevelData(   500,  2));
        lvls.add(new LevelData(  1500,  4));
        lvls.add(new LevelData(  3000,  6));
        lvls.add(new LevelData(  5000,  8));
        lvls.add(new LevelData(  7500, 16));
        lvls.add(new LevelData( 10000, 24));
        lvls.add(new LevelData( 15000, 32));
        lvls.add(new LevelData( 20000, 48));
        lvls.add(new LevelData( 30000, 64));
        return new UpgradeConfig(true, true, 10, lvls);
    }

    public record LevelData(int cost, int itemsPerTransfer) {}
}
