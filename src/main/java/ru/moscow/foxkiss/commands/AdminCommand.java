package ru.moscow.foxkiss.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.moscow.foxkiss.FMAuction;

import java.util.List;
import java.util.Map;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final FMAuction plugin;

    public AdminCommand(FMAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = playerOrNull(sender);

        if (!sender.hasPermission("fmauction.admin")) {
            sendMessage(sender, player, "admin-nopermission");
            return true;
        }

        if (args.length != 1) {
            sendMessage(sender, player, "admin-unknown-subcommand");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReload(sender, player);
            return true;
        }
        sendMessage(sender, player, "admin-unknown-subcommand");
        return true;
    }

    private void handleReload(CommandSender sender, Player player) {
        long start = System.currentTimeMillis();

        try {
            plugin.reloadAll();
            sendMessage(sender, player, "admin-reload", Map.of("time", String.valueOf(System.currentTimeMillis() - start)));
        } catch (Exception e) {
            sendMessage(sender, player, "admin-error-reload", Map.of("error", e.getClass().getSimpleName()));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("fmauction.admin")) {
            return List.of();
        }

        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }

        return List.of();
    }

    private Player playerOrNull(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    private void sendMessage(CommandSender sender, Player player, String key) {
        sender.sendMessage(plugin.getMessageManager().getMessage(player, key));
    }

    private void sendMessage(CommandSender sender, Player player, String key, Map<String, String> placeholders) {
        sender.sendMessage(plugin.getMessageManager().getMessage(player, key, placeholders));
    }
}
