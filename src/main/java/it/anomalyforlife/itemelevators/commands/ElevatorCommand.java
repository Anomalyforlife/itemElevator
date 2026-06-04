package it.anomalyforlife.itemelevators.commands;

import it.anomalyforlife.itemelevators.ItemElevators;
import it.anomalyforlife.itemelevators.elevator.Elevator;
import it.anomalyforlife.itemelevators.elevator.ElevatorManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ElevatorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("reload", "list", "remove");

    private final ItemElevators plugin;

    public ElevatorCommand(ItemElevators plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0) {
            plugin.getLangManager().send(sender, "command.usage");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "list"   -> handleList(sender);
            case "remove" -> handleRemove(sender);
            default       -> plugin.getLangManager().send(sender, "command.usage");
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-commands
    // -------------------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("itemelevators.reload")) {
            plugin.getLangManager().send(sender, "command.no-permission");
            return;
        }
        plugin.reloadAll();
        plugin.getLangManager().send(sender, "command.reload");
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("itemelevators.list")) {
            plugin.getLangManager().send(sender, "command.no-permission");
            return;
        }

        ElevatorManager manager = plugin.getElevatorManager();
        int count = manager.getAllElevators().size();

        plugin.getLangManager().send(sender, "command.list-header", "{count}", String.valueOf(count));

        if (count == 0) {
            plugin.getLangManager().send(sender, "command.list-empty");
            return;
        }

        for (Elevator elevator : manager.getAllElevators()) {
            Location b = elevator.getBottomChest();
            Location t = elevator.getTopChest();
            int level = plugin.getUpgradeService().getLevel(elevator);
            plugin.getLangManager().send(sender, "command.list-entry",
                    "{world}", elevator.getWorldName(),
                    "{bx}", String.valueOf(b.getBlockX()),
                    "{by}", String.valueOf(b.getBlockY()),
                    "{bz}", String.valueOf(b.getBlockZ()),
                    "{tx}", String.valueOf(t.getBlockX()),
                    "{ty}", String.valueOf(t.getBlockY()),
                    "{tz}", String.valueOf(t.getBlockZ()),
                    "{level}", String.valueOf(level));
        }
    }

    private void handleRemove(CommandSender sender) {
        if (!sender.hasPermission("itemelevators.remove")) {
            plugin.getLangManager().send(sender, "command.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /ie remove.");
            return;
        }

        // Check the block the player is looking at (max 5 blocks)
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            plugin.getLangManager().send(player, "command.remove-fail");
            return;
        }

        ElevatorManager manager = plugin.getElevatorManager();
        Optional<Elevator> elevator = manager.getElevatorAt(target.getLocation());
        if (elevator.isEmpty()) {
            plugin.getLangManager().send(player, "command.remove-fail");
            return;
        }

        manager.unregisterElevator(elevator.get());
        manager.saveData();
        plugin.getLangManager().send(player, "command.remove-success");
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String label,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
