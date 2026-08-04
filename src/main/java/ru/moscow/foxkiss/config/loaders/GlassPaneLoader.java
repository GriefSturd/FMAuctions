package ru.moscow.foxkiss.config.loaders;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;

import java.util.*;

public final class GlassPaneLoader {
    public static Map<Integer, ConfigValues.GlassPane> load(List<Map<?, ?>> entries) {
        Map<Integer, ConfigValues.GlassPane> panes = new HashMap<>();

        for (Map<?, ?> entry : entries) {
            Material material = Material.matchMaterial((String) entry.get("glass-type"));
            String name = (String) entry.get("display-name");
            Integer modelData = entry.containsKey("custom-model-data")
                    ? (Integer) entry.get("custom-model-data")
                    : null;

            ConfigValues.GlassPane pane = new ConfigValues.GlassPane(material, name, modelData);

            if (entry.containsKey("slots")) {
                for (Integer slot : (List<Integer>) entry.get("slots")) {
                    panes.put(slot, pane);
                }
            } else {
                panes.put((Integer) entry.get("slot"), pane);
            }
        }

        return Map.copyOf(panes);
    }
}