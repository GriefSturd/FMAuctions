package ru.moscow.foxkiss.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaceholderUtils {

    private static boolean enabled;

    private PlaceholderUtils() {
    }

    public static void init(JavaPlugin plugin) {
        enabled = plugin.getServer()
                .getPluginManager()
                .isPluginEnabled("PlaceholderAPI");
    }

    public static String setPlaceholders(Player player, String text) {
        if (!enabled) {
            return text;
        }

        return PlaceholderAPI.setPlaceholders(player, text);
    }
}