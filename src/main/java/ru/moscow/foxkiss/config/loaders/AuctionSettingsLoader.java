package ru.moscow.foxkiss.config.loaders;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.gui.enums.ActionType;

import java.util.*;

public final class AuctionSettingsLoader {
    private final JavaPlugin plugin;

    public AuctionSettingsLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ConfigValues.AuctionData load(ConfigurationSection auction, ConfigurationSection categoriesSection) {
        CategoryData categories = loadCategories(categoriesSection);
        ConfigurationSection exit = auction.getConfigurationSection("exit-action");

        return new ConfigValues.AuctionData(
                auction.getInt("max-storage-days"),
                auction.getInt("menu-size"),
                Set.copyOf(auction.getIntegerList("active-slots")),
                categories.materials(),
                categories.allCategories(),
                exit.getInt("slot"),
                loadButtonConfig(exit)
        );
    }

    private CategoryData loadCategories(ConfigurationSection section) {
        Map<String, Set<Material>> materials = new HashMap<>();
        Set<String> all = new HashSet<>();

        for (String key : section.getKeys(false)) {
            String category = key.toLowerCase(Locale.ROOT);
            Set<Material> mats = EnumSet.noneOf(Material.class);
            for (String raw : section.getStringList(key)) {
                raw = raw.trim();
                if (raw.equalsIgnoreCase("all")) {
                    all.add(category);
                } else {
                    Material m = Material.matchMaterial(raw);
                    if (m == null) {
                        plugin.getLogger().warning("Неизвестный материал в категории " + key + ": " + raw);
                    } else {
                        mats.add(m);
                    }
                }
            }
            materials.put(category, Set.copyOf(mats));
        }
        return new CategoryData(Map.copyOf(materials), Set.copyOf(all));
    }

    private ConfigValues.ButtonConfig loadButtonConfig(ConfigurationSection section) {
        MaterialParser.ParsedMaterial parsed = MaterialParser.parse(section, false);
        String name = section.getString("name", "");
        String title = section.getString("title", name);
        return new ConfigValues.ButtonConfig(
                parsed.material(),
                title.isEmpty() ? name : title,
                List.copyOf(section.getStringList("lore")),
                parsed.skullTexture(),
                loadAction(section),
                SlotLoader.loadSlots(section),
                loadModelData(section)
        );
    }

    private ActionType loadAction(ConfigurationSection section) {
        List<String> actions = section.getStringList("actions");
        if (actions.isEmpty()) return null;
        String first = actions.get(0).trim();
        if (first.startsWith("[") && first.endsWith("]")) {
            first = first.substring(1, first.length() - 1);
        }
        return ActionType.get(first);
    }

    private Integer loadModelData(ConfigurationSection section) {
        return section.isSet("custom-model-data") ? section.getInt("custom-model-data") : null;
    }

    private record CategoryData(Map<String, Set<Material>> materials, Set<String> allCategories) {}
}