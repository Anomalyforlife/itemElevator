package it.anomalyforlife.itemelevators.elevator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.gui.ElevatorGUI;

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
    /** Keyed by the specific chest location being viewed, not the elevator. */
    private final Map<Location, ElevatorGUI> openGUIs = new HashMap<>();

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

        ConfigurationSection chainsSection = data.getConfigurationSection("chains");
        if (chainsSection != null) {
            int loaded = 0;
            for (String k : chainsSection.getKeys(false)) {
                try {
                    String worldName = chainsSection.getString(k + ".world");
                    if (worldName == null) continue;
                    World world = plugin.getServer().getWorld(worldName);
                    if (world == null) {
                        plugin.getLogger().warning("World '" + worldName + "' not loaded; chain " + k + " skipped.");
                        continue;
                    }

                    List<Map<?, ?>> rawChests = chainsSection.getMapList(k + ".chests");
                    if (rawChests.size() < 2) continue;

                    List<Location> chestNodes = new ArrayList<>();
                    for (Map<?, ?> rawChest : rawChests) {
                        Integer x = asInt(rawChest.get("x"));
                        Integer y = asInt(rawChest.get("y"));
                        Integer z = asInt(rawChest.get("z"));
                        if (x == null || y == null || z == null) {
                            chestNodes.clear();
                            break;
                        }
                        chestNodes.add(new Location(world, x, y, z));
                    }

                    if (chestNodes.size() < 2) continue;
                    int level = chainsSection.getInt(k + ".level", 1);
                    registerChain(chestNodes, level);
                    loaded++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load chain '" + k + "': " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + loaded + " chain(s) from storage.");
            return;
        }

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

    private void registerChain(List<Location> chestNodes, int level) {
        for (int i = 0; i < chestNodes.size() - 1; i++) {
            Elevator elevator = new Elevator(chestNodes.get(i), chestNodes.get(i + 1));
            registerElevator(elevator);
            plugin.getUpgradeService().registerElevator(elevator, level);
        }
    }

    public void saveData() {
        if (data == null) return;
        data.set("chains", null);
        data.set("elevators", null);

        int idx = 0;
        Set<Elevator> visited = new HashSet<>();
        for (Elevator elevator : elevators) {
            if (!visited.add(elevator)) continue;

            List<Elevator> chain = getChain(elevator);
            visited.addAll(chain);
            if (chain.isEmpty()) continue;

            String path = "chains." + idx++;
            List<Location> chestNodes = new ArrayList<>();
            chestNodes.add(chain.getFirst().getBottomChest());
            for (Elevator link : chain) {
                chestNodes.add(link.getTopChest());
            }

            data.set(path + ".world", chain.getFirst().getWorldName());
            data.set(path + ".chests", serializeChainNodes(chestNodes));
            data.set(path + ".level", getChainLevel(chain));
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot save elevators.yml: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> serializeChainNodes(List<Location> chestNodes) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Location chest : chestNodes) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("x", chest.getBlockX());
            node.put("y", chest.getBlockY());
            node.put("z", chest.getBlockZ());
            serialized.add(node);
        }
        return serialized;
    }

    private int getChainLevel(List<Elevator> chain) {
        int level = Integer.MAX_VALUE;
        for (Elevator elevator : chain) {
            level = Math.min(level, plugin.getUpgradeService().getLevel(elevator));
        }
        return level == Integer.MAX_VALUE ? 1 : level;
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        return null;
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
        return detectElevator(chestLocation, 0);
    }

    /**
     * Detects the nearest elevator pair in a specific direction.
     * direction > 0 searches upward only, direction < 0 searches downward only,
     * direction == 0 keeps the original up-then-down behavior.
     */
    public Optional<Elevator> detectElevator(Location chestLocation, int direction) {
        Block block = chestLocation.getBlock();
        ElevatorItem elevatorItem = plugin.getElevatorItem();
        if (!elevatorItem.isElevatorBlock(block)) return Optional.empty();

        int maxDist = plugin.getConfigManager().getMaxDistance();

        if (direction > 0) {
            return searchColumn(block, elevatorItem, maxDist, 1);
        }
        if (direction < 0) {
            return searchColumn(block, elevatorItem, maxDist, -1);
        }

        Optional<Elevator> up = searchColumn(block, elevatorItem, maxDist, 1);
        if (up.isPresent()) return up;
        return searchColumn(block, elevatorItem, maxDist, -1);
    }

    /**
     * Detects the full vertical chain that passes through the given chest.
     * The returned list is ordered from bottom to top.
     */
    public List<Elevator> detectChain(Location chestLocation) {
        Block block = chestLocation.getBlock();
        ElevatorItem elevatorItem = plugin.getElevatorItem();
        if (!elevatorItem.isElevatorBlock(block)) return List.of();

        LinkedList<Elevator> chain = new LinkedList<>();

        Location cursor = chestLocation;
        while (true) {
            Optional<Elevator> detected = detectElevator(cursor, -1);
            if (detected.isEmpty()) break;

            Elevator below = detected.get();
            chain.addFirst(below);
            cursor = below.getBottomChest();
        }

        cursor = chestLocation;
        while (true) {
            Optional<Elevator> detected = detectElevator(cursor, 1);
            if (detected.isEmpty()) break;

            Elevator above = detected.get();
            chain.addLast(above);
            cursor = above.getTopChest();
        }

        return new ArrayList<>(chain);
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

        // Close any open GUIs for either chest of this elevator
        for (Location loc : List.of(elevator.getBottomChest(), elevator.getTopChest())) {
            ElevatorGUI gui = openGUIs.remove(key(loc));
            if (gui != null) gui.closeAll();
        }
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

    /**
     * Returns all elevators that form a continuous vertical chain with the
     * given elevator, ordered from bottom to top.
     */
    public List<Elevator> getChain(Elevator elevator) {
        LinkedList<Elevator> chain = new LinkedList<>();
        chain.add(elevator);

        // Walk down: find elevators whose top chest is our bottom's top → i.e., someone feeds into our bottom
        Elevator below = topIndex.get(key(elevator.getBottomChest()));
        while (below != null) {
            chain.addFirst(below);
            below = topIndex.get(key(below.getBottomChest()));
        }

        // Walk up: find elevators whose bottom chest is our top chest
        Elevator above = bottomIndex.get(key(elevator.getTopChest()));
        while (above != null) {
            chain.addLast(above);
            above = bottomIndex.get(key(above.getTopChest()));
        }

        return new ArrayList<>(chain);
    }

    public Set<Elevator> getAllElevators() {
        return Collections.unmodifiableSet(elevators);
    }

    // -------------------------------------------------------------------------
    // GUI management
    // -------------------------------------------------------------------------

    public ElevatorGUI getOrCreateGUI(Elevator elevator, Location chestLocation) {
        return openGUIs.computeIfAbsent(key(chestLocation),
                k -> new ElevatorGUI(plugin, elevator, chestLocation));
    }

    public ElevatorGUI getGUI(Location chestLocation) {
        return openGUIs.get(key(chestLocation));
    }

    public void markGUIClosed(Location chestLocation) {
        openGUIs.remove(key(chestLocation));
    }

    public void closeAllGUIs() {
        for (ElevatorGUI gui : new ArrayList<>(openGUIs.values())) {
            gui.closeAll();
            gui.closeIfInactive();
        }
        openGUIs.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Location key(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
