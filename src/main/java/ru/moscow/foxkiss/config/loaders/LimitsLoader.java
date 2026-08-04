package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public final class LimitsLoader {
    public static Map<String, Integer> load(ConfigurationSection section) {
        Map<String, Integer> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key.toLowerCase(), section.getInt(key));
        }
        return Map.copyOf(result);
    }
}