package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.ProfileMenuConfig;
import ru.moscow.foxkiss.gui.actions.Action;

import java.util.ArrayList;
import java.util.List;

public final class ProfileMenuLoader {

    public ProfileMenuConfig load(ConfigurationSection root) {
        ConfigurationSection menu = root.getConfigurationSection("menu");
        ConfigurationSection button = root.getConfigurationSection("button");
        ConfigurationSection itemsSection = root.getConfigurationSection("items");
        List<ConfigValues.ButtonConfig> items = new ArrayList<>();

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
            if (!itemSection.getBoolean("enabled", true)) continue;
            items.add(ButtonLoader.load(itemSection, true));
        }

        return new ProfileMenuConfig(
                root.getBoolean("enabled"),
                menu.getString("title"),
                menu.getInt("size"),
                root.getInt("max-length"),
                root.getString("empty-value"),
                ButtonLoader.load(button, true),
                List.copyOf(items)
        );
    }
}
