package ru.moscow.foxkiss.config.loaders;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.gui.enums.ActionType;

import java.util.List;

public final class GuiLoader {

    private final FileConfiguration config;

    public GuiLoader(FileConfiguration config) {
        this.config = config;
    }

    public ConfigValues.GuiConfig load(ConfigurationSection auction) {
        return new ConfigValues.GuiConfig(
                loadTitles(auction),
                loadSortMenu(auction),
                loadItemLore(),
                loadQuantityMenu(auction),
                loadNavigation(auction),
                loadCategoryMenu(auction),
                loadButton(auction.getConfigurationSection("exit-action"))
        );
    }


    public ConfigValues.ConfirmMenuConfig loadConfirmMenu(ConfigurationSection auction) {
        ConfigurationSection sec = auction.getConfigurationSection("confirm-menu");

        return new ConfigValues.ConfirmMenuConfig(
                sec.getBoolean("enable-confirm-menu", true),
                sec.getInt("item-slot"),
                sec.getInt("size"),
                loadConfirmButton(sec.getConfigurationSection("confirm")),
                loadConfirmButton(sec.getConfigurationSection("cancel")),
                GlassPaneLoader.load(sec.getMapList("glass"))
        );
    }


    private ConfigValues.TitlesConfig loadTitles(ConfigurationSection auction) {
        ConfigurationSection titles = auction.getConfigurationSection("titles");

        return new ConfigValues.TitlesConfig(
                titles.getString("main"),
                titles.getString("selling"),
                titles.getString("expired"),
                titles.getString("quantity"),
                titles.getString("confirm-buy")
        );
    }


    private ConfigValues.SortMenuConfig loadSortMenu(ConfigurationSection auction) {
        ConfigurationSection s = auction.getConfigurationSection("sort-menu");

        return new ConfigValues.SortMenuConfig(
                Material.matchMaterial(s.getString("material")),
                s.getString("name"),
                s.getString("selected-prefix"),
                s.getString("unselected-prefix"),
                s.getString("footer")
        );
    }


    private ConfigValues.ItemLoreConfig loadItemLore() {
        ConfigurationSection symbol = config.getConfigurationSection("symbol_value");

        return new ConfigValues.ItemLoreConfig(
                symbol.getConfigurationSection("item-lore").getStringList("lore"),
                symbol.getConfigurationSection("item-lore_one").getStringList("lore"),
                symbol.getConfigurationSection("item-lore_buy_one_item").getStringList("lore")
        );
    }


    private ConfigValues.QuantityMenuConfig loadQuantityMenu(ConfigurationSection auction) {
        ConfigurationSection q = auction.getConfigurationSection("quantity-menu");

        return new ConfigValues.QuantityMenuConfig(
                q.getInt("slot-amount"),
                q.getInt("size-menu"),
                loadButton(q.getConfigurationSection("decrease-10")),
                loadButton(q.getConfigurationSection("decrease-1")),
                loadButton(q.getConfigurationSection("amount")),
                loadButton(q.getConfigurationSection("increase-1")),
                loadButton(q.getConfigurationSection("increase-10")),
                GlassPaneLoader.load(q.getMapList("glass"))
        );
    }


    private ConfigValues.NavigationConfig loadNavigation(ConfigurationSection auction) {
        ConfigurationSection nav = auction.getConfigurationSection("navigation");

        return new ConfigValues.NavigationConfig(
                loadNavButton(nav.getConfigurationSection("previous")),
                loadNavButton(nav.getConfigurationSection("refresh")),
                loadNavButton(nav.getConfigurationSection("next")),
                loadNavButton(nav.getConfigurationSection("selling")),
                loadNavButton(nav.getConfigurationSection("expired")),
                loadNavButton(nav.getConfigurationSection("sort")),
                loadNavButton(nav.getConfigurationSection("categories"))
        );
    }


    private ConfigValues.CategoryMenuConfig loadCategoryMenu(ConfigurationSection auction) {
        ConfigurationSection c = auction.getConfigurationSection("category-menu");

        return new ConfigValues.CategoryMenuConfig(
                Material.matchMaterial(c.getString("material")),
                c.getString("name"),
                c.getString("selected-prefix"),
                c.getString("unselected-prefix"),
                c.getString("footer")
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
                loadAction(section),
                loadModelData(section)
        );
    }


    private ConfigValues.ButtonConfig loadButton(ConfigurationSection section) {
        MaterialParser.ParsedMaterial parsed = MaterialParser.parse(section);
        return new ConfigValues.ButtonConfig(
                parsed.material(),
                section.getString("name", ""),
                List.copyOf(section.getStringList("lore")),
                parsed.skullTexture(),
                loadAction(section),
                SlotLoader.loadSlots(section),
                loadModelData(section)
        );
    }

    private ConfigValues.NavigationButton loadNavButton(ConfigurationSection section) {
        MaterialParser.ParsedMaterial parsed = MaterialParser.parse(section);

        return new ConfigValues.NavigationButton(
                section.getInt("slot"),
                parsed.material(),
                section.getString("name"),
                section.getStringList("lore"),
                parsed.skullTexture(),
                loadAction(section),
                loadModelData(section)
        );
    }

    private ActionType loadAction(ConfigurationSection section) {
        List<String> actions = section.getStringList("actions");

        String first = actions.getFirst().trim();

        if (first.startsWith("[") && first.endsWith("]")) {
            first = first.substring(1, first.length() - 1);
        }

        ActionType action = ActionType.get(first);

        return action;
    }


    private Integer loadModelData(ConfigurationSection section) {
        return section.isSet("custom-model-data")
                ? section.getInt("custom-model-data")
                : null;
    }
}