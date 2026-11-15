package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.SalesHistoryConfig;
import ru.moscow.foxkiss.gui.actions.Action;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SalesHistoryLoader {

    public SalesHistoryConfig load(ConfigurationSection root) {
        ConfigurationSection menu = root.getConfigurationSection("menu");
        ConfigurationSection button = root.getConfigurationSection("button");
        ConfigurationSection entry = root.getConfigurationSection("entry");
        ConfigurationSection empty = root.getConfigurationSection("empty");
        ConfigurationSection itemsSection = root.getConfigurationSection("items");

        List<ConfigValues.ButtonConfig> items = new ArrayList<>();
        Set<Integer> reserved = new LinkedHashSet<>();

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
            if (!itemSection.getBoolean("enabled", true)) continue;

            ConfigValues.ButtonConfig item = ButtonLoader.load(itemSection, true);
            reserved.addAll(item.slots());
            items.add(item);
        }

        List<Integer> slots = new ArrayList<>();
        for (int slot = menu.getInt("start-slot") - 1, end = menu.getInt("end-slot"); slot < end; slot++) {
            if (!reserved.contains(slot)) slots.add(slot);
        }

        return new SalesHistoryConfig(
                root.getBoolean("enabled"),
                menu.getString("title"),
                menu.getInt("size"),
                List.copyOf(slots),
                ButtonLoader.load(button, true),
                entry.getBoolean("use-original-item"),
                ButtonLoader.load(entry, false),
                ButtonLoader.load(empty, true),
                List.copyOf(items)
        );
    }
}
