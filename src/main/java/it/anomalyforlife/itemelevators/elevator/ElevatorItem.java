package it.anomalyforlife.itemelevators.elevator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class ElevatorItem {

    private final NamespacedKey markerKey;
    private final NamespacedKey levelKey;

    public ElevatorItem(Plugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "elevator_chest");
        this.levelKey  = new NamespacedKey(plugin, "elevator_level");
    }

    // -------------------------------------------------------------------------
    // Item helpers
    // -------------------------------------------------------------------------

    public ItemStack create(int level) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Item Elevator ✦", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Livello: " + level, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Piazzalo per creare un elevator", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BOOLEAN, true);
        pdc.set(levelKey,  PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isElevatorItem(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    public int getItemLevel(ItemStack item) {
        if (!isElevatorItem(item)) return 1;
        Integer level = item.getItemMeta().getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return level != null ? level : 1;
    }

    // -------------------------------------------------------------------------
    // Block helpers — read/write PDC on placed chest tile-states
    // -------------------------------------------------------------------------

    public boolean isElevatorBlock(Block block) {
        if (block.getType() != Material.CHEST) return false;
        if (!(block.getState() instanceof TileState ts)) return false;
        return ts.getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    public void markBlock(Block block, int level) {
        if (!(block.getState() instanceof TileState ts)) return;
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BOOLEAN, true);
        pdc.set(levelKey,  PersistentDataType.INTEGER, level);
        ts.update();
    }

    public int getBlockLevel(Block block) {
        if (!(block.getState() instanceof TileState ts)) return 1;
        Integer level = ts.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return level != null ? level : 1;
    }
}
