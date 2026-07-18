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

    private static final String PERMISSION = "fmauction.admin";
    private static final String RELOAD = "reload";

    private final FMAuction plugin;

    public AdminCommand(FMAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = sender instanceof Player p ? p : null;

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(plugin.getMessageManager().getMessage(player, "admin-nopermission"));
            return true;
        }

        if (args.length != 1 || !RELOAD.equalsIgnoreCase(args[0])) {
            sender.sendMessage(plugin.getMessageManager().getMessage(player, "admin-unknown-subcommand"));
            return true;
        }

        reload(sender);
        return true;
    }

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();

        try {
            plugin.reloadAll();

            sender.sendMessage(plugin.getMessageManager().getMessage(
                    sender instanceof Player p ? p : null,
                    "admin-reload",
                    Map.of("time", String.valueOf(System.currentTimeMillis() - start))
            ));
        } catch (Exception exception) {
            sender.sendMessage(plugin.getMessageManager().getMessage(
                    sender instanceof Player p ? p : null,
                    "admin-error-reload",
                    Map.of("error", exception.getClass().getSimpleName())
            ));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length == 1 && RELOAD.startsWith(args[0].toLowerCase())) {
            return List.of(RELOAD);
        }

        return List.of();
    }
}