package ru.moscow.foxkiss.utils.managers.interfaces;

import org.bukkit.command.CommandSender;
import ru.moscow.foxkiss.config.ConfigValues;

import java.util.List;
import java.util.Map;

public interface IMessageManager {

    void reload(ConfigValues config);

    String getMessage(CommandSender sender, String key);

    String getMessage(CommandSender sender, String key, Map<String, String> replacements);
}
