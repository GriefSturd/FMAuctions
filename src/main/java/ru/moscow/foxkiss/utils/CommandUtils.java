package ru.moscow.foxkiss.utils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;

import java.util.Collections;
import java.util.List;

public final class CommandUtils {

    public static List<String> getResult(String[] args, List<String> completions) {
        if (completions == null || completions.isEmpty() || args == null || args.length == 0) {
            return Collections.emptyList();
        }
        String last = args[args.length - 1];
        ObjectList<String> result = new ObjectArrayList<>();
        for (String c : completions) {
            if (StringUtil.startsWithIgnoreCase(c, last)) {
                result.add(c);
            }
        }
        return result;
    }

    public static Double parsePrice(CommandSender sender, String[] args, IConfigManager configManager) {
        if (args.length < 2) {
            sender.sendMessage(PlaceholderUtils.applypapi(sender, configManager.getConfigValues().messages().noPrice(), configManager));
            return null;
        }
        try {
            return Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PlaceholderUtils.applypapi(sender, configManager.getConfigValues().messages().noPrice(), configManager));
            return null;
        }
    }
}
