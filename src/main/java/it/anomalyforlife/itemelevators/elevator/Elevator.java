package it.anomalyforlife.itemelevators.elevator;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public class Elevator {

    private final Location bottomChest;
    private final Location topChest;

    public Elevator(Location bottomChest, Location topChest) {
        this.bottomChest = toBlock(bottomChest);
        this.topChest = toBlock(topChest);
    }

    /** Both chunks must be loaded for the elevator to operate. */
    public boolean isChunksLoaded() {
        World world = bottomChest.getWorld();
        if (world == null) return false;
        return world.isChunkLoaded(bottomChest.getBlockX() >> 4, bottomChest.getBlockZ() >> 4)
                && world.isChunkLoaded(topChest.getBlockX() >> 4, topChest.getBlockZ() >> 4);
    }

    public Location getBottomChest() {
        return bottomChest.clone();
    }

    public Location getTopChest() {
        return topChest.clone();
    }

    public String getWorldName() {
        World world = bottomChest.getWorld();
        return world != null ? world.getName() : "unknown";
    }

    private static Location toBlock(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Elevator other)) return false;
        return Objects.equals(bottomChest, other.bottomChest)
                && Objects.equals(topChest, other.topChest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bottomChest, topChest);
    }

    @Override
    public String toString() {
        return "Elevator{bottom=" + fmtLoc(bottomChest) + ", top=" + fmtLoc(topChest) + "}";
    }

    private String fmtLoc(Location l) {
        return "(" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
    }
}
