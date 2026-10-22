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
        ConfigurationSection a = root.getConfigurationSection("menu");
        ConfigurationSection b = root.getConfigurationSection("button");
        ConfigurationSection c = root.getConfigurationSection("entry");
        ConfigurationSection d = root.getConfigurationSection("empty");
        ConfigurationSection e = root.getConfigurationSection("items");

        List<ConfigValues.ButtonConfig> items = new ArrayList<>();
        Set<Integer> reserved = new LinkedHashSet<>();

        for (String key : e.getKeys(false)) {
            ConfigurationSection f = e.getConfigurationSection(key);
            if (!f.getBoolean("enabled", true)) continue;

            ConfigValues.ButtonConfig item = loadItem(f, true);
            reserved.addAll(item.slots());
            items.add(item);
        }

        List<Integer> slots = new ArrayList<>();
        for (int slot = a.getInt("start-slot") - 1, end = a.getInt("end-slot"); slot < end; slot++) {
            if (!reserved.contains(slot)) slots.add(slot);
        }

        return new SalesHistoryConfig(
                root.getBoolean("enabled"),
                a.getString("title"),
                a.getInt("size"),
                List.copyOf(slots),
                loadItem(b, true),
                c.getBoolean("use-original-item"),
                loadItem(c, false),
                loadItem(d, true),
                List.copyOf(items)
        );
    }

    private ConfigValues.ButtonConfig loadItem(ConfigurationSection section, boolean withSlots) {
        MaterialParser.ParsedMaterial parsed = MaterialParser.parse(section);
        List<Integer> slots = new ArrayList<>();

        if (withSlots) {
            for (int slot : SlotLoader.loadSlots(section)) {
                slots.add(slot - 1);
            }
        }

        List<Action> actions = new ArrayList<>();
        for (String raw : section.getStringList("actions")) {
            actions.add(Action.fromString(raw));
        }

        return new ConfigValues.ButtonConfig(
                parsed.material(),
                section.getString("display_name"),
                List.copyOf(section.getStringList("lore")),
                parsed.skullTexture(),
                List.copyOf(actions),
                List.copyOf(slots),
                section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null
        );
    }
}
