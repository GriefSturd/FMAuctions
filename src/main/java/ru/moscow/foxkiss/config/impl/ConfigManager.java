package ru.moscow.foxkiss.config.impl;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.config.loaders.*;
import ru.moscow.foxkiss.utils.ItemUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ConfigManager implements IConfigManager {
    private final JavaPlugin plugin;
    private ConfigValues configValues;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    @Override
    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        loadItemsYml();

        FileConfiguration config = plugin.getConfig();

        ConfigurationSection dbSection = config.getConfigurationSection("database");
        DataBaseLoader dbLoader = new DataBaseLoader();
        DataBaseLoader.DataBase db = dbLoader.load(dbSection);

        String prefix = config.getString("prefix");
        MessagesLoader messagesLoader = new MessagesLoader(prefix);
        ConfigValues.ConfigMessages messages = messagesLoader.load(config);

        ConfigurationSection cooldownsSection = config.getConfigurationSection("cooldowns");
        CooldownLoader cooldownsLoader = new CooldownLoader();
        ConfigValues.Cooldowns cooldowns = cooldownsLoader.load(cooldownsSection);

        ConfigurationSection priceLimitsSection = config.getConfigurationSection("price-limits");
        PriceLimitsLoader priceLimitsLoader = new PriceLimitsLoader();
        ConfigValues.PriceLimits priceLimits = priceLimitsLoader.load(priceLimitsSection);

        ConfigurationSection vaultAuctionSection = config.getConfigurationSection("vault-auc");
        ConfigurationSection playerPAuctionSection = config.getConfigurationSection("playerpoints-auc");
        ConfigurationSection groupsVaultSection = vaultAuctionSection.getConfigurationSection("groups");
        ConfigurationSection prioritiesVaultSection = vaultAuctionSection.getConfigurationSection("priorities");

        ConfigurationSection groupsPlayerPSection = playerPAuctionSection.getConfigurationSection("groups");
        ConfigurationSection prioritiesPlayerPSection = playerPAuctionSection.getConfigurationSection("priorities");

        Map<String, Integer> vaultGroups = LimitsLoader.load(groupsVaultSection);
        Map<String, Integer> vaultPriorities = LimitsLoader.load(prioritiesVaultSection);
        Map<String, Integer> ppGroups = LimitsLoader.load(groupsPlayerPSection);
        Map<String, Integer> ppPriorities = LimitsLoader.load(prioritiesPlayerPSection);

        ConfigurationSection auctionSection = config.getConfigurationSection("auction");
        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");

        AuctionSettingsLoader auctionLoader = new AuctionSettingsLoader(plugin);
        ConfigValues.AuctionData auctionSettings = auctionLoader.load(auctionSection, categoriesSection);

        Map<Integer, ConfigValues.GlassPane> sellingGlass = GlassPaneLoader.load(config.getMapList("menu-glass-selling"));
        Map<Integer, ConfigValues.GlassPane> expiredGlass = GlassPaneLoader.load(config.getMapList("menu-glass-expired"));

        ConfigurationSection menuGlass = config.getConfigurationSection("menu-glass");
        Map<Integer, ConfigValues.GlassPane> vaultGlass = GlassPaneLoader.load(menuGlass.getMapList("vault"));
        Map<Integer, ConfigValues.GlassPane> ppGlass = GlassPaneLoader.load(menuGlass.getMapList("playerpoints"));

        ConfigurationSection auction = config.getConfigurationSection("auction");
        ConfigurationSection sorting = auction.getConfigurationSection("sorting");
        ConfigurationSection categoryNamesSection = auction.getConfigurationSection("category-names");
        Map<String, String> sortingNames = loadStringMap(sorting, false);
        Map<String, String> categoryNames = loadStringMap(categoryNamesSection, true);

        ConfigurationSection sectionSymbol = config.getConfigurationSection("symbol_value");
        SymbolsLoader.Symbols symbols = new SymbolsLoader().load(sectionSymbol);

        GuiLoader guiLoader = new GuiLoader(config);
        ConfigValues.GuiConfig gui = guiLoader.load(config.getConfigurationSection("auction"));
        ConfigValues.ConfirmMenuConfig confirmMenu = guiLoader.loadConfirmMenu(auctionSection);

        boolean enableBstats = config.getBoolean("enable-bstats");
        boolean usePapi = config.getBoolean("use-papi");

        this.configValues = new ConfigValues(
                db.host(), db.port(), db.username(), db.password(), db.database(),
                prefix,
                vaultGroups, vaultPriorities,
                ppGroups, ppPriorities,
                auctionSettings.maxStorageDays(),
                auctionSettings.menuSize(),
                auctionSettings.activeSlots(),
                auctionSettings.categories(),
                auctionSettings.allCategories(),
                sellingGlass, expiredGlass, vaultGlass, ppGlass,
                messages,
                sortingNames, categoryNames,
                symbols.money(), symbols.rubles(),
                auctionSettings.exitSlot(),
                auctionSettings.exitButton(),
                gui,
                confirmMenu,
                cooldowns,
                priceLimits,
                enableBstats,
                usePapi
        );
    }

    @Override
    public ConfigValues getConfigValues() {
        return configValues;
    }

    private Map<String, String> loadStringMap(ConfigurationSection section, boolean lowerCaseKeys) {
        Map<String, String> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(lowerCaseKeys ? key.toLowerCase() : key, section.getString(key));
        }
        return Map.copyOf(map);
    }

    private void loadItemsYml() {
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) {
            plugin.saveResource("items.yml", false);
        }
        ItemUtils.loadTranslations(file);
    }
}