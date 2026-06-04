package it.anomalyforlife.itemelevators;

import java.util.Objects;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import it.anomalyforlife.itemelevators.commands.ElevatorCommand;
import it.anomalyforlife.itemelevators.config.ConfigManager;
import it.anomalyforlife.itemelevators.config.LangManager;
import it.anomalyforlife.itemelevators.elevator.ElevatorItem;
import it.anomalyforlife.itemelevators.elevator.ElevatorManager;
import it.anomalyforlife.itemelevators.listeners.ChestListener;
import it.anomalyforlife.itemelevators.listeners.InventoryListener;
import it.anomalyforlife.itemelevators.tasks.ElevatorTask;
import it.anomalyforlife.itemelevators.upgrade.UpgradeConfig;
import it.anomalyforlife.itemelevators.upgrade.UpgradeService;
import net.milkbowl.vault.economy.Economy;

public class ItemElevators extends JavaPlugin {

    private static ItemElevators instance;

    private ConfigManager configManager;
    private LangManager langManager;
    private ElevatorItem elevatorItem;
    private ElevatorManager elevatorManager;
    private UpgradeService upgradeService;
    private Economy economy;
    private ElevatorTask elevatorTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        configManager = new ConfigManager(this);
        langManager = new LangManager(this);
        elevatorItem = new ElevatorItem(this);

        setupEconomy();

        UpgradeConfig upgradeCfg = UpgradeConfig.fromSection(
                getConfig().getConfigurationSection("upgrades"));
        upgradeService = new UpgradeService(upgradeCfg, economy);

        elevatorManager = new ElevatorManager(this);

        getServer().getPluginManager().registerEvents(new ChestListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        ElevatorCommand cmd = new ElevatorCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("itemelevator"));
        command.setExecutor(cmd);
        command.setTabCompleter(cmd);

        startElevatorTask();

        getLogger().info("ItemElevators v" + getPluginMeta().getVersion() + " enabled! (by Anomalyforlife)");
    }

    @Override
    public void onDisable() {
        if (elevatorManager != null) {
            elevatorManager.closeAllGUIs();
        }
        if (elevatorTask != null) {
            elevatorTask.cancel();
        }
        if (elevatorManager != null) {
            elevatorManager.saveData();
        }
        getLogger().info("ItemElevators disabled.");
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault not found — economy features disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Vault found but no economy provider is registered.");
            return;
        }
        economy = rsp.getProvider();
        getLogger().info("Hooked into Vault economy: " + economy.getName());
    }

    private void startElevatorTask() {
        int interval = configManager.getTransferInterval();
        elevatorTask = new ElevatorTask(this);
        elevatorTask.runTaskTimer(this, interval, interval);
    }

    public void restartTask() {
        if (elevatorTask != null) elevatorTask.cancel();
        startElevatorTask();
    }

    public void reloadAll() {
        configManager.reload();
        langManager.reload();
        upgradeService.updateConfig(UpgradeConfig.fromSection(
                getConfig().getConfigurationSection("upgrades")));
        restartTask();
    }

    public static ItemElevators getInstance() { return instance; }
    public ConfigManager getConfigManager()   { return configManager; }
    public LangManager getLangManager()       { return langManager; }
    public ElevatorItem getElevatorItem()     { return elevatorItem; }
    public ElevatorManager getElevatorManager() { return elevatorManager; }
    public UpgradeService getUpgradeService() { return upgradeService; }
    public Economy getEconomy()               { return economy; }
    public boolean hasEconomy()               { return economy != null; }
}
