package it.anomalyforlife.itemelevators.config;

import it.anomalyforlife.itemelevators.ItemElevators;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final ItemElevators plugin;

    private int transferInterval;
    private int maxDistance;
    private boolean economyEnabled;
    private double creationCost;
    private String language;

    public ConfigManager(ItemElevators plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        transferInterval = Math.max(1, cfg.getInt("transfer-interval", 10));
        maxDistance      = Math.max(1, cfg.getInt("max-distance", 10));
        economyEnabled   = cfg.getBoolean("economy.enabled", false);
        creationCost     = Math.max(0, cfg.getDouble("economy.creation-cost", 100.0));
        language         = cfg.getString("language", "en");
    }

    public int getTransferInterval() { return transferInterval; }
    public int getMaxDistance()      { return maxDistance; }
    public boolean isEconomyEnabled(){ return economyEnabled; }
    public double getCreationCost()  { return creationCost; }
    public String getLanguage()      { return language; }
}
