package it.anomalyforlife.itemelevators.elevator;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.gui.ElevatorGUI;
import org.bukkit.Location;
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

    /**
     * A chest can act as the BOTTOM of one elevator AND the TOP of another
     * simultaneously, enabling chaining (A→B→C via two independent pairs).
     * These two maps enforce the constraint that each chest fills each role at
     * most once.
     */
    private final Map<Location, Elevator> bottomIndex = new HashMap<>();
    private final Map<Location, Elevator> topIndex    = new HashMap<>();

    private final Set<Elevator> elevators = new LinkedHashSet<>();
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
        for (String k : section.getKeys(false)) {
            try {
                String worldName = section.getString(k + ".world");
                if (worldName == null) continue;
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not loaded; elevator " + k + " skipped.");
                    continue;
                }

                Location bottom = new Location(world,
                        section.getInt(k + ".bottom.x"),
                        section.getInt(k + ".bottom.y"),
                        section.getInt(k + ".bottom.z"));

                Location top = new Location(world,
                        section.getInt(k + ".top.x"),
                        section.getInt(k + ".top.y"),
                        section.getInt(k + ".top.z"));

                int level = section.getInt(k + ".level", 1);

                Elevator elevator = new Elevator(bottom, top);
                registerElevator(elevator);
                plugin.getUpgradeService().registerElevator(elevator, level);
                loaded++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load elevator '" + k + "': " + e.getMessage());
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
            Location top    = elevator.getTopChest();
            String path = "elevators." + idx++;
            data.set(path + ".world",    elevator.getWorldName());
            data.set(path + ".bottom.x", bottom.getBlockX());
            data.set(path + ".bottom.y", bottom.getBlockY());
            data.set(path + ".bottom.z", bottom.getBlockZ());
            data.set(path + ".top.x",    top.getBlockX());
            data.set(path + ".top.y",    top.getBlockY());
            data.set(path + ".top.z",    top.getBlockZ());
            data.set(path + ".level",    plugin.getUpgradeService().getLevel(elevator));
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
     * Scans above and below the given location for the nearest elevator block
     * that can be paired with it. No conductor blocks are required — the two
     * chests only need to share X/Z and be within {@code max-distance} blocks
     * vertically.
     *
     * <p>Rules to support chaining (A→B→C):
     * <ul>
     *   <li>When scanning UP, a candidate that is already the TOP of another
     *       elevator is skipped (each chest can be someone's top only once).</li>
     *   <li>When scanning DOWN, a candidate that is already the BOTTOM of
     *       another elevator is skipped.</li>
     * </ul>
     */
    public Optional<Elevator> detectElevator(Location chestLocation) {
        Block block = chestLocation.getBlock();
        ElevatorItem elevatorItem = plugin.getElevatorItem();
        if (!elevatorItem.isElevatorBlock(block)) return Optional.empty();

        int maxDist = plugin.getConfigManager().getMaxDistance();

        Optional<Elevator> up = searchColumn(block, elevatorItem, maxDist, 1);
        if (up.isPresent()) return up;
        return searchColumn(block, elevatorItem, maxDist, -1);
    }

    private Optional<Elevator> searchColumn(Block origin, ElevatorItem elevatorItem, int maxDist, int direction) {
        for (int dist = 1; dist <= maxDist; dist++) {
            Block candidate = origin.getRelative(0, dist * direction, 0);
            if (!elevatorItem.isElevatorBlock(candidate)) continue;

            Location candKey = key(candidate.getLocation());
            if (direction > 0) {
                // origin will be bottom, candidate will be top — skip if already someone's top
                if (topIndex.containsKey(candKey)) continue;
            } else {
                // origin will be top, candidate will be bottom — skip if already someone's bottom
                if (bottomIndex.containsKey(candKey)) continue;
            }

            Location bottom = direction > 0 ? origin.getLocation() : candidate.getLocation();
            Location top    = direction > 0 ? candidate.getLocation() : origin.getLocation();
            return Optional.of(new Elevator(bottom, top));
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public void registerElevator(Elevator elevator) {
        elevators.add(elevator);
        bottomIndex.put(key(elevator.getBottomChest()), elevator);
        topIndex.put(key(elevator.getTopChest()), elevator);
    }

    public void unregisterElevator(Elevator elevator) {
        elevators.remove(elevator);
        bottomIndex.remove(key(elevator.getBottomChest()));
        topIndex.remove(key(elevator.getTopChest()));
        plugin.getUpgradeService().unregisterElevator(elevator);

        ElevatorGUI gui = openGUIs.remove(elevator);
        if (gui != null) gui.closeAll();
    }

    /**
     * Invalidates every elevator that contains the given location (either as
     * bottom or top). Returns true if at least one elevator was removed.
     */
    public boolean invalidateAt(Location location) {
        Location k = key(location);
        List<Elevator> toRemove = new ArrayList<>();

        Elevator asBottom = bottomIndex.get(k);
        Elevator asTop    = topIndex.get(k);
        if (asBottom != null) toRemove.add(asBottom);
        if (asTop != null && asTop != asBottom) toRemove.add(asTop);

        toRemove.forEach(this::unregisterElevator);
        return !toRemove.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Returns true if this location is the bottom OR top of any registered elevator.
     */
    public boolean isElevatorChest(Location location) {
        Location k = key(location);
        return bottomIndex.containsKey(k) || topIndex.containsKey(k);
    }

    /**
     * Returns the elevator where this location is the BOTTOM (preferred), or
     * where it is the TOP if no bottom-elevator exists here.
     */
    public Optional<Elevator> getElevatorAt(Location location) {
        Location k = key(location);
        Elevator e = bottomIndex.get(k);
        if (e != null) return Optional.of(e);
        return Optional.ofNullable(topIndex.get(k));
    }

    /**
     * Returns the elevator where this location is the BOTTOM only.
     * Used to decide whether to attempt detection of a new pair.
     */
    public Optional<Elevator> getBottomElevatorAt(Location location) {
        return Optional.ofNullable(bottomIndex.get(key(location)));
    }

    /**
     * Returns the highest upgrade level among all elevators containing this
     * location. Used when dropping the item after a break.
     */
    public int getMaxLevelAt(Location location) {
        Location k = key(location);
        int level = 1;
        Elevator b = bottomIndex.get(k);
        Elevator t = topIndex.get(k);
        if (b != null) level = Math.max(level, plugin.getUpgradeService().getLevel(b));
        if (t != null) level = Math.max(level, plugin.getUpgradeService().getLevel(t));
        return level;
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
