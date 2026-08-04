package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public final class SlotLoader {

    public static List<Integer> loadSlots(ConfigurationSection section) {
        List<Integer> slots = section.getIntegerList("slots");
        if (!slots.isEmpty()) {
            return List.copyOf(slots);
        }
        int single = section.getInt("slot", -1);
        if (single != -1) {
            return List.of(single);
        }
        throw new IllegalStateException(
                "В секции " + section.getCurrentPath() + " не задан ни 'slots', ни 'slot'"
        );
    }
}