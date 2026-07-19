package ru.moscow.foxkiss.utils.managers;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.utils.TextUtils;
import ru.moscow.foxkiss.utils.managers.interfaces.IMessageManager;

import java.util.HashMap;
import java.util.Map;

public final class MessageManager implements IMessageManager {

    private static final String MISSING_MESSAGE = "&cСообщение не найдено: ";

    private final Map<String, String> rawMessages;
    private final boolean placeholderApiEnabled;
    private String prefix;

    public MessageManager(ConfigValues config) {
        this.rawMessages = new HashMap<>();
        this.placeholderApiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        reload(config);
    }

    @Override
    public void reload(ConfigValues config) {
        rawMessages.clear();
        rawMessages.putAll(config.messages());
        prefix = rawMessages.getOrDefault("prefix", "");
    }

    @Override
    public String getMessage(CommandSender sender, String key) {
        return format(sender, key, Map.of());
    }

    @Override
    public String getMessage(CommandSender sender, String key, Map<String, String> replacements) {
        return format(sender, key, replacements);
    }

    private String format(CommandSender sender, String key, Map<String, String> replacements) {
        String message = rawMessages.get(key);
        if (message == null) {
            message = MISSING_MESSAGE + key;
        }

        message = message.replace("%prefix%", prefix);

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        message = applyPlaceholderApi(sender, message);

        return TextUtils.colorize(message);
    }

    private String applyPlaceholderApi(CommandSender sender, String message) {
        if (!placeholderApiEnabled) {
            return message;
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        return PlaceholderAPI.setPlaceholders(player, message);
    }
}