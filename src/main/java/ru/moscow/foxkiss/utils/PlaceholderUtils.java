package ru.moscow.foxkiss.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;

public final class PlaceholderUtils {

    public static String apply(CommandSender sender, String message, IConfigManager configManager) {
        if (!configManager.getConfigValues().usePapi()
                || !(sender instanceof Player player)
                || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return message;
        }
        return PlaceholderAPI.setPlaceholders(player, message);
    }

    public static Component applypapi(CommandSender sender, String message, IConfigManager configManager) {
        String processed = apply(sender, message, configManager);
        return TextUtils.component(processed);
    }
}
