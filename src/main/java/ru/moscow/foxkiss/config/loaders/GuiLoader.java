package ru.moscow.foxkiss.config.loaders;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.gui.actions.Action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuiLoader {

    public ConfigValues.AuctionGuiConfig loadMenu(ConfigurationSection section) {
        ConfigurationSection a = section.getConfigurationSection("symbol_value");
        ConfigurationSection b = section.getConfigurationSection("sorting");
        ConfigurationSection c = section.getConfigurationSection("category-names");
        ConfigurationSection d = section.getConfigurationSection("exit-action");
        SymbolsLoader.Symbols symbols = new SymbolsLoader().load(a);

        List<Integer> slots = section.getIntegerList("active-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < 36; i++) slots.add(i);
        }

        return new ConfigValues.AuctionGuiConfig(
                section.getInt("menu-size"),
                List.copyOf(slots),
                loadTitles(section),
                loadStringMap(b, false),
                loadStringMap(c, true),
                symbols.symbol(),
                loadSortMenu(section),
                loadCategoryMenu(section),
                loadItemLore(section),
                loadNavigation(section),
                loadButton(d),
                GlassPaneLoader.load(section.getMapList("glass-selling")),
                GlassPaneLoader.load(section.getMapList("glass-expired"))
        );
    }

    public ConfigValues.ConfirmMenuConfig loadConfirmMenu(ConfigurationSection section) {
        ConfigurationSection a = section.getConfigurationSection("confirm");
        ConfigurationSection b = section.getConfigurationSection("cancel");
        return new ConfigValues.ConfirmMenuConfig(
                section.getString("title", ""),
                section.getBoolean("enable-confirm-menu", true),
                section.getInt("item-slot"),
                section.getInt("size"),
                loadConfirmButton(a),
                loadConfirmButton(b),
                GlassPaneLoader.load(section.getMapList("glass"))
        );
    }

    public ConfigValues.QuantityMenuConfig loadQuantityMenu(ConfigurationSection section) {
        ConfigurationSection a = section.getConfigurationSection("decrease-10");
        ConfigurationSection b = section.getConfigurationSection("decrease-1");
        ConfigurationSection c = section.getConfigurationSection("amount");
        ConfigurationSection d = section.getConfigurationSection("increase-1");
        ConfigurationSection e = section.getConfigurationSection("increase-10");
        return new ConfigValues.QuantityMenuConfig(
                section.getString("title", ""),
                section.getInt("slot-amount"),
                section.getInt("size-menu"),
                loadButton(a),
                loadButton(b),
                loadButton(c),
                loadButton(d),
                loadButton(e),
                GlassPaneLoader.load(section.getMapList("glass"))
        );
    }

    private ConfigValues.TitlesConfig loadTitles(ConfigurationSection section) {
        ConfigurationSection a = section.getConfigurationSection("titles");
        return new ConfigValues.TitlesConfig(
                a.getString("main"),
                a.getString("selling"),
                a.getString("expired")
        );
    }

    private ConfigValues.SortMenuConfig loadSortMenu(ConfigurationSection section) {
        ConfigurationSection a = section.getConfigurationSection("sort-menu");
        return new ConfigValues.SortMenuConfig(
                Material.matchMaterial(a.getString("material")),
                a.getString("name"),
                a.getString("selected-prefix"),
                a.getString("unselected-prefix"),
                a.getString("footer")
        );
    }

    private ConfigValues.ItemLoreConfig loadItemLore(ConfigurationSection section) {
        ConfigurationSection a = section.getConfigurationSection("symbol_value");
        ConfigurationSection b = a.getConfigurationSection("item-lore");
        ConfigurationSection c = a.getConfigurationSection("item-lore_one");
        ConfigurationSection d = a.getConfigurationSection("item-lore_buy_one_item");
        return new ConfigValues.ItemLoreConfig(
                b.getStringList("lore"),
                c.getStringList("lore"),
                d.getStringList("lore")
        );
    }

    private ConfigValues.CategoryMenuConfig loadCategoryMenu(ConfigurationSection section) {
        ConfigurationSection a = section.getConfigurationSection("category-menu");
        return new ConfigValues.CategoryMenuConfig(
                Material.matchMaterial(a.getString("material")),
                a.getString("name"),
                a.getString("selected-prefix"),
                a.getString("unselected-prefix"),
                a.getString("footer")
        );
    }

    private ConfigValues.NavigationConfig loadNavigation(ConfigurationSection section) {
        ConfigurationSection a = section.getConfigurationSection("navigation");
        ConfigurationSection b = a.getConfigurationSection("previous");
        ConfigurationSection c = a.getConfigurationSection("refresh");
        ConfigurationSection d = a.getConfigurationSection("next");
        ConfigurationSection e = a.getConfigurationSection("selling");
        ConfigurationSection f = a.getConfigurationSection("expired");
        ConfigurationSection g = a.getConfigurationSection("sort");
        ConfigurationSection h = a.getConfigurationSection("categories");
        return new ConfigValues.NavigationConfig(
                loadNavButton(b),
                loadNavButton(c),
                loadNavButton(d),
                loadNavButton(e),
                loadNavButton(f),
                loadNavButton(g),
                loadNavButton(h)
        );
    }

    private ConfigValues.ConfirmButtonConfig loadConfirmButton(ConfigurationSection section) {
        MaterialParser.ParsedMaterial parsed = MaterialParser.parse(section);
        return new ConfigValues.ConfirmButtonConfig(
                parsed.material(),
                section.getString("name", ""),
                section.getStringList("lore"),
                parsed.skullTexture(),
                SlotLoader.loadSlots(section),
                loadActions(section),
                loadModelData(section)
        );
    }

    private ConfigValues.ButtonConfig loadButton(ConfigurationSection section) {
        MaterialParser.ParsedMaterial t = MaterialParser.parse(section);
        return new ConfigValues.ButtonConfig(
                t.material(),
                section.getString("name", ""),
                List.copyOf(section.getStringList("lore")),
                t.skullTexture(),
                loadActions(section),
                SlotLoader.loadSlots(section),
                loadModelData(section)
        );
    }

    private ConfigValues.NavigationButton loadNavButton(ConfigurationSection section) {
        MaterialParser.ParsedMaterial u = MaterialParser.parse(section);
        return new ConfigValues.NavigationButton(
                section.getInt("slot"),
                u.material(),
                section.getString("name"),
                section.getStringList("lore"),
                u.skullTexture(),
                loadActions(section),
                loadModelData(section)
        );
    }

    private List<Action> loadActions(ConfigurationSection section) {
        List<Action> actions = new ArrayList<>();
        for (String raw : section.getStringList("actions")) {
            Action action = Action.fromString(raw);
            if (action != null) {
                actions.add(action);
            }
        }
        return List.copyOf(actions);
    }

    private Integer loadModelData(ConfigurationSection section) {
        return section.isSet("custom-model-data")
                ? section.getInt("custom-model-data")
                : null;
    }

    private Map<String, String> loadStringMap(ConfigurationSection section, boolean lowerCaseKeys) {
        Map<String, String> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(lowerCaseKeys ? key.toLowerCase() : key, section.getString(key));
        }
        return Map.copyOf(map);
    }
}
