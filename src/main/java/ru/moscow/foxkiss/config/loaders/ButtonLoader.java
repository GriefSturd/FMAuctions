package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.gui.actions.Action;

import java.util.ArrayList;
import java.util.List;

public final class ButtonLoader {
    public static ConfigValues.ButtonConfig load(ConfigurationSection section, boolean withSlots) {
        return load(section, withSlots, section.getString("display_name"), 1);
    }

    static ConfigValues.ButtonConfig loadAuctionButton(ConfigurationSection section) {
        String name = section.getString("name", section.getString("display_name", ""));
        return load(section, true, name, 0);
    }

    private static ConfigValues.ButtonConfig load(ConfigurationSection section, boolean withSlots, String name, int slotOffset) {
        MaterialParser.ParsedMaterial parsed = MaterialParser.parse(section);
        List<Action> actions = new ArrayList<>();
        for (String raw : section.getStringList("actions")) {
            Action action = Action.fromString(raw);
            if (action != null) actions.add(action);
        }
        List<Integer> slots = new ArrayList<>();
        if (withSlots) {
            for (int slot : SlotLoader.loadSlots(section)) {
                slots.add(slot - slotOffset);
            }
        }
        return new ConfigValues.ButtonConfig(parsed.material(), name,
                List.copyOf(section.getStringList("lore")), parsed.skullTexture(), List.copyOf(actions),
                List.copyOf(slots), section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null);
    }
}
