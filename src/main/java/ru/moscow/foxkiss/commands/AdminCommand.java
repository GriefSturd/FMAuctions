package ru.moscow.foxkiss.commands;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.List;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final FMAuction plugin;
    private final IConfigManager configManager;

    public AdminCommand(FMAuction plugin, IConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("fmauction.admin")) {
            sender.sendMessage(PlaceholderUtils.applypapi(sender, configManager.getConfigValues().messages().noPermission(), configManager));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(PlaceholderUtils.applypapi(sender, configManager.getConfigValues().messages().unknownSubcommand(), configManager));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }
        sender.sendMessage(PlaceholderUtils.applypapi(sender, configManager.getConfigValues().messages().unknownSubcommand(), configManager));
        return true;
    }

    private void handleReload(CommandSender sender) {
        long start = System.currentTimeMillis();

        try {
            plugin.reloadAll();
            sender.sendMessage(PlaceholderUtils.applypapi(sender, configManager.getConfigValues().messages().reload().replace("{time}", String.valueOf(System.currentTimeMillis() - start)), configManager));
        } catch (Exception e) {
            sender.sendMessage(PlaceholderUtils.applypapi(sender, configManager.getConfigValues().messages().errorReload().replace("{error}", e.getClass().getSimpleName()), configManager));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("fmauction.admin")) {
            return List.of();
        }

        final ObjectList<String> completions = new ObjectArrayList<>();
        if (args.length == 1) {
            completions.add("reload");
        }

        return getResult(args, completions);
    }

    private ObjectList<String> getResult(String[] args, ObjectList<String> completions) {
        if (completions.isEmpty()) {
            return completions;
        }
        final ObjectList<String> result = new ObjectArrayList<>();
        for (int i = 0; i < completions.size(); i++) {
            String c = completions.get(i);
            if (StringUtil.startsWithIgnoreCase(c, args[args.length - 1])) {
                result.add(c);
            }
        }
        return result;
    }
}