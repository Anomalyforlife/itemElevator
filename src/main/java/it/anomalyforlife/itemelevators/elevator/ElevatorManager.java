package it.anomalyforlife.itemelevators.elevator;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.gui.ElevatorGUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ElevatorManager {

    private final ItemElevators plugin;

    /** Both chest locations (block-snapped) map to the same Elevator instance. */
    private final Map<Location, Elevator> chestIndex = new HashMap<>();

    /** Canonical set of all registered elevators. */
    private final Set<Elevator> elevators = new LinkedHashSet<>();

    /** Open GUIs — multiple viewers share one ElevatorGUI instance per elevator. */
    private final Map<Elevator, ElevatorGUI> openGUIs = new HashMap<>();

    private File dataFile;
    private FileConfiguration data;

    public ElevatorManager(ItemElevators plugin) {
        this.plugin = plugin;
        loadData();
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "elevators.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Cannot create elevators.yml: " + e.getMessage());
                return;
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection section = data.getConfigurationSection("elevators");
        if (section == null) return;

        int loaded = 0;
        for (String key : section.getKeys(false)) {
            try {
                String worldName = section.getString(key + ".world");
                if (worldName == null) continue;
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not loaded; elevator " + key + " skipped.");
                    continue;
                }

                Location bottom = new Location(world,
                        section.getInt(key + ".bottom.x"),
                        section.getInt(key + ".bottom.y"),
                        section.getInt(key + ".bottom.z"));

                Location top = new Location(world,
                        section.getInt(key + ".top.x"),
                        section.getInt(key + ".top.y"),
                        section.getInt(key + ".top.z"));

                int level = section.getInt(key + ".level", 1);

                Elevator elevator = new Elevator(bottom, top);
                registerElevator(elevator);
                plugin.getUpgradeService().registerElevator(elevator, level);
                loaded++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load elevator '" + key + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + loaded + " elevator(s) from storage.");
    }

    public void saveData() {
        if (data == null) return;
        data.set("elevators", null);

        int idx = 0;
        for (Elevator elevator : elevators) {
            Location bottom = elevator.getBottomChest();
            Location top = elevator.getTopChest();
            String path = "elevators." + idx++;
            data.set(path + ".world", elevator.getWorldName());
            data.set(path + ".bottom.x", bottom.getBlockX());
            data.set(path + ".bottom.y", bottom.getBlockY());
            data.set(path + ".bottom.z", bottom.getBlockZ());
            data.set(path + ".top.x", top.getBlockX());
            data.set(path + ".top.y", top.getBlockY());
            data.set(path + ".top.z", top.getBlockZ());
            data.set(path + ".level", plugin.getUpgradeService().getLevel(elevator));
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot save elevators.yml: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Detection
    // -------------------------------------------------------------------------

    /**
     * Scans above/below the given chest to find a valid elevator pair.
     * Requires at least one conductor block between the two chests with no gaps.
     */
    public Optional<Elevator> detectElevator(Location chestLocation) {
        Block block = chestLocation.getBlock();
        if (block.getType() != Material.CHEST) return Optional.empty();

        Material conductor = plugin.getConfigManager().getConductorMaterial();
        int maxDist = plugin.getConfigManager().getMaxDistance();

        Optional<Elevator> up = searchColumn(block, conductor, maxDist, 1);
        if (up.isPresent()) return up;

        return searchColumn(block, conductor, maxDist, -1);
    }

    private Optional<Elevator> searchColumn(Block origin, Material conductor, int maxDist, int direction) {
        int conductorCount = 0;
        for (int dist = 1; dist <= maxDist; dist++) {
            Block candidate = origin.getRelative(0, dist * direction, 0);
            Material type = candidate.getType();

            if (type == conductor) {
                conductorCount++;
                continue;
            }
            if (type == Material.CHEST) {
                if (conductorCount == 0) break;
                Location bottom = direction > 0 ? origin.getLocation() : candidate.getLocation();
                Location top    = direction > 0 ? candidate.getLocation() : origin.getLocation();
                return Optional.of(new Elevator(bottom, top));
            }
            break;
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public void registerElevator(Elevator elevator) {
        elevators.add(elevator);
        chestIndex.put(key(elevator.getBottomChest()), elevator);
        chestIndex.put(key(elevator.getTopChest()), elevator);
    }

    public void unregisterElevator(Elevator elevator) {
        elevators.remove(elevator);
        chestIndex.remove(key(elevator.getBottomChest()));
        chestIndex.remove(key(elevator.getTopChest()));
        plugin.getUpgradeService().unregisterElevator(elevator);

        ElevatorGUI gui = openGUIs.remove(elevator);
        if (gui != null) gui.closeAll();
    }

    public boolean invalidateAt(Location location) {
        Elevator elevator = chestIndex.get(key(location));
        if (elevator == null) return false;
        unregisterElevator(elevator);
        return true;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public boolean isElevatorChest(Location location) {
        return chestIndex.containsKey(key(location));
    }

    public Optional<Elevator> getElevatorAt(Location location) {
        return Optional.ofNullable(chestIndex.get(key(location)));
    }

    public Set<Elevator> getAllElevators() {
        return Collections.unmodifiableSet(elevators);
    }

    // -------------------------------------------------------------------------
    // GUI management
    // -------------------------------------------------------------------------

    public ElevatorGUI getOrCreateGUI(Elevator elevator) {
        return openGUIs.computeIfAbsent(elevator, e -> new ElevatorGUI(plugin, e));
    }

    public void markGUIClosed(Elevator elevator) {
        openGUIs.remove(elevator);
    }

    public boolean hasOpenGUI(Elevator elevator) {
        return openGUIs.containsKey(elevator);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Location key(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
