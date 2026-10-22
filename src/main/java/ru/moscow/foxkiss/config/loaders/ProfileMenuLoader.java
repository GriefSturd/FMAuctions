package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.ProfileMenuConfig;
import ru.moscow.foxkiss.gui.actions.Action;

import java.util.ArrayList;
import java.util.List;

public final class ProfileMenuLoader {

    public ProfileMenuConfig load(ConfigurationSection root) {
        ConfigurationSection a = root.getConfigurationSection("menu");
        ConfigurationSection b = root.getConfigurationSection("button");
        ConfigurationSection c = root.getConfigurationSection("items");
        List<ConfigValues.ButtonConfig> items = new ArrayList<>();

        for (String key : c.getKeys(false)) {
            ConfigurationSection d = c.getConfigurationSection(key);
            if (!d.getBoolean("enabled", true)) continue;
            items.add(loadItem(d));
        }

        return new ProfileMenuConfig(root.getBoolean("enabled"), a.getString("title"), a.getInt("size"),
                root.getInt("max-length"), root.getString("empty-value"), loadItem(b), List.copyOf(items));
    }

    private ConfigValues.ButtonConfig loadItem(ConfigurationSection section) {
        MaterialParser.ParsedMaterial parsed = MaterialParser.parse(section);
        List<Action> actions = new ArrayList<>();

        for (String raw : section.getStringList("actions")) {
            Action action = Action.fromString(raw);
            if (action != null) actions.add(action);
        }

        List<Integer> slots = new ArrayList<>();
        for (int slot : SlotLoader.loadSlots(section)) {
            slots.add(slot - 1);
        }

        return new ConfigValues.ButtonConfig(parsed.material(), section.getString("display_name"),
                List.copyOf(section.getStringList("lore")), parsed.skullTexture(), List.copyOf(actions),
                List.copyOf(slots), section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null);
    }
}
