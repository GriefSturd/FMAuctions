package ru.moscow.foxkiss.config.impl;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import java.io.File;
import java.util.*;

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

        ConfigurationSection database = config.getConfigurationSection("database");
        String dbHost = database.getString("host");
        int dbPort = database.getInt("port");
        String dbUser = database.getString("username");
        String dbPass = database.getString("password");
        String dbName = database.getString("database");

        String prefix = config.getString("prefix");
        boolean bStatsEnabled = config.getBoolean("enable-bstats");
        boolean usePapi = config.getBoolean("use-papi", true);

        ConfigurationSection cooldowns = config.getConfigurationSection("cooldowns");
        ConfigValues.Cooldowns cooldownValues = new ConfigValues.Cooldowns(
                Math.max(0.0D, cooldowns.getDouble("update-auc")),
                Math.max(0.0D, cooldowns.getDouble("take-item")),
                cooldowns.getBoolean("enable-cooldown-message"),
                cooldowns.getBoolean("enable-cooldown")
        );

        ConfigurationSection priceLimitsSection = config.getConfigurationSection("price-limits");
        if (priceLimitsSection == null) {
            plugin.getLogger().warning("Секция 'price-limits' не найдена в config.yml! Используются значения по умолчанию.");
            priceLimitsSection = config.createSection("price-limits");
        }
        ConfigValues.PriceLimits priceLimits = new ConfigValues.PriceLimits(
                priceLimitsSection.getDouble("min-price-money-auc", 100.0),
                priceLimitsSection.getDouble("min-price-money-dauc", 150.0),
                priceLimitsSection.getDouble("max-price-money-auc", 100_000_000.0),
                priceLimitsSection.getDouble("max-price-money-dauc", 1_000_000.0)
        );

        ConfigurationSection vaultAuc = config.getConfigurationSection("vault-auc");
        ConfigurationSection ppAuc = config.getConfigurationSection("playerpoints-auc");
        Map<String, Integer> vaultGroupLimits = loadLimits(vaultAuc.getConfigurationSection("groups"));
        Map<String, Integer> vaultPriorityLimits = loadLimits(vaultAuc.getConfigurationSection("priorities"));
        Map<String, Integer> ppGroupLimits = loadLimits(ppAuc.getConfigurationSection("groups"));
        Map<String, Integer> ppPriorityLimits = loadLimits(ppAuc.getConfigurationSection("priorities"));

        ConfigurationSection auction = config.getConfigurationSection("auction");
        int maxStorageDays = auction.getInt("max-storage-days");
        int menuSize = auction.getInt("menu-size");
        Set<Integer> activeSlots = new HashSet<>(auction.getIntegerList("active-slots"));

        Map<String, Set<Material>> categoryMaterials = new HashMap<>();
        Set<String> allMaterialCategories = new HashSet<>();
        ConfigurationSection categories = config.getConfigurationSection("categories");
        for (String cat : categories.getKeys(false)) {
            String normalizedCat = cat.toLowerCase();
            Set<Material> materials = EnumSet.noneOf(Material.class);
            for (String raw : categories.getStringList(cat)) {
                String trimmed = raw.trim();
                if ("all".equalsIgnoreCase(trimmed)) {
                    allMaterialCategories.add(normalizedCat);
                    continue;
                }
                Material mat = Material.matchMaterial(trimmed);
                if (mat == null) {
                    plugin.getLogger().warning("Неизвестный материал в категории " + cat + ": " + trimmed);
                    continue;
                }
                materials.add(mat);
            }
            categoryMaterials.put(normalizedCat, Collections.unmodifiableSet(materials));
        }
        categoryMaterials = Map.copyOf(categoryMaterials);
        allMaterialCategories = Set.copyOf(allMaterialCategories);

        ConfigurationSection menuGlass = config.getConfigurationSection("menu-glass");
        Map<Integer, ConfigValues.GlassPane> sellingGlass = loadGlassPanes(config.getMapList("menu-glass-selling"));
        Map<Integer, ConfigValues.GlassPane> expiredGlass = loadGlassPanes(config.getMapList("menu-glass-expired"));
        Map<Integer, ConfigValues.GlassPane> vaultGlass = loadGlassPanes(menuGlass.getMapList("vault"));
        Map<Integer, ConfigValues.GlassPane> ppGlass = loadGlassPanes(menuGlass.getMapList("playerpoints"));

        ConfigValues.ConfigMessages messages = loadMessagesConfig(config);

        ConfigurationSection sortingSection = auction.getConfigurationSection("sorting");
        Map<String, String> sortingNames = new HashMap<>();
        for (String key : sortingSection.getKeys(false)) {
            sortingNames.put(key, sortingSection.getString(key));
        }
        sortingNames = Map.copyOf(sortingNames);

        ConfigurationSection categoryNamesSection = auction.getConfigurationSection("category-names");
        Map<String, String> categoryNames = new HashMap<>();
        for (String key : categoryNamesSection.getKeys(false)) {
            categoryNames.put(key.toLowerCase(), categoryNamesSection.getString(key));
        }
        categoryNames = Map.copyOf(categoryNames);

        ConfigurationSection symbolSection = config.getConfigurationSection("symbol_value");
        String symbolVault = symbolSection.getString("money");
        String symbolPlayerPoints = symbolSection.getString("rubles");

        ConfigValues.GuiConfig guiConfig = loadGuiConfig(auction);
        ConfigValues.ConfirmMenuConfig confirmMenuConfig = loadConfirmMenuConfig(auction);

        int exitSlot = auction.getInt("exit-action.slot");
        ConfigValues.ButtonConfig exitButton = loadButtonConfig(auction.getConfigurationSection("exit-action"));

        configValues = new ConfigValues(
                dbHost, dbPort, dbUser, dbPass, dbName,
                prefix,
                vaultGroupLimits, vaultPriorityLimits,
                ppGroupLimits, ppPriorityLimits,
                maxStorageDays, menuSize, activeSlots,
                categoryMaterials, allMaterialCategories,
                sellingGlass, expiredGlass, vaultGlass, ppGlass,
                messages, sortingNames, categoryNames,
                symbolVault, symbolPlayerPoints,
                exitSlot, exitButton, guiConfig,
                confirmMenuConfig, cooldownValues, priceLimits, bStatsEnabled, usePapi
        );
    }

    @Override
    public ConfigValues getConfigValues() {
        return configValues;
    }

    private Map<String, Integer> loadLimits(ConfigurationSection section) {
        Map<String, Integer> limits = new HashMap<>();
        for (String key : section.getKeys(false)) {
            limits.put(key.toLowerCase(), section.getInt(key));
        }
        return Map.copyOf(limits);
    }

    private Map<Integer, ConfigValues.GlassPane> loadGlassPanes(List<Map<?, ?>> list) {
        Map<Integer, ConfigValues.GlassPane> panes = new HashMap<>();
        for (Map<?, ?> raw : list) {
            String glassType = String.valueOf(raw.get("glass-type"));
            Object displayNameObj = raw.get("display-name");
            String displayName = displayNameObj != null ? String.valueOf(displayNameObj) : "";
            Material mat = Material.matchMaterial(glassType);
            if (raw.containsKey("slots")) {
                if (raw.get("slots") instanceof List<?> slotsList) {
                    for (Object slotObj : slotsList) {
                        if (slotObj instanceof Number n) {
                            panes.put(n.intValue(), new ConfigValues.GlassPane(mat, displayName));
                        }
                    }
                }
            } else if (raw.containsKey("slot")) {
                panes.put(((Number) raw.get("slot")).intValue(), new ConfigValues.GlassPane(mat, displayName));
            }
        }
        return Map.copyOf(panes);
    }

    private ConfigValues.GuiConfig loadGuiConfig(ConfigurationSection auction) {
        return new ConfigValues.GuiConfig(
                loadTitlesConfig(auction),
                loadSortMenuConfig(auction),
                loadItemLoreConfig(auction),
                loadQuantityMenuConfig(auction),
                loadNavigationConfig(auction),
                loadCategoryMenuConfig(auction)
        );
    }

    private ConfigValues.ConfirmMenuConfig loadConfirmMenuConfig(ConfigurationSection auction) {
        ConfigurationSection sec = auction.getConfigurationSection("confirm-menu");
        boolean enabled = sec.getBoolean("enable-confirm-menu", true);
        int itemSlot = sec.getInt("item-slot");
        int size = sec.getInt("size");
        ConfigValues.ConfirmButtonConfig confirm = loadConfirmButton(sec.getConfigurationSection("confirm"));
        ConfigValues.ConfirmButtonConfig cancel = loadConfirmButton(sec.getConfigurationSection("cancel"));
        Map<Integer, ConfigValues.GlassPane> glassPanes = loadGlassPanes(sec.getMapList("glass"));
        return new ConfigValues.ConfirmMenuConfig(enabled, itemSlot, size, confirm, cancel, glassPanes);
    }

    private ConfigValues.ConfirmButtonConfig loadConfirmButton(ConfigurationSection section) {
        String itemStr = section.getString("item", "");
        String materialStr = section.getString("material", "");
        Material material = null;
        String skullTexture = null;
        if (itemStr.startsWith("basehead-")) {
            material = Material.PLAYER_HEAD;
            skullTexture = itemStr.substring("basehead-".length());
        } else if (!itemStr.isEmpty()) {
            material = Material.matchMaterial(itemStr);
        }
        if (material == null && !materialStr.isEmpty()) {
            material = Material.matchMaterial(materialStr);
        }

        ActionType action = loadActionFromList(section);
        String name = section.getString("name", "");
        List<String> lore = section.getStringList("lore");

        List<Integer> slots = section.getIntegerList("slots");
        if (slots.isEmpty()) {
            int singleSlot = section.getInt("slot", -1);
            if (singleSlot >= 0) {
                slots = List.of(singleSlot);
            } else {
                slots = List.of();
            }
        }

        return new ConfigValues.ConfirmButtonConfig(material, name, lore, skullTexture, slots, action);
    }

    private ConfigValues.TitlesConfig loadTitlesConfig(ConfigurationSection auction) {
        ConfigurationSection titles = auction.getConfigurationSection("titles");
        String main = titles.getString("main");
        String selling = titles.getString("selling");
        String expired = titles.getString("expired");
        String quantity = titles.getString("quantity");
        String confirmBuy = titles.getString("confirm-buy");
        return new ConfigValues.TitlesConfig(
                main,
                selling,
                expired,
                quantity,
                confirmBuy
        );
    }

    private ConfigValues.SortMenuConfig loadSortMenuConfig(ConfigurationSection auction) {
        ConfigurationSection sortMenu = auction.getConfigurationSection("sort-menu");
        String name = sortMenu.getString("name");
        String selectedPrefix = sortMenu.getString("selected-prefix");
        String unselectedPrefix = sortMenu.getString("unselected-prefix");
        String footer = sortMenu.getString("footer");
        return new ConfigValues.SortMenuConfig(
                Material.matchMaterial(sortMenu.getString("material")),
                name,
                selectedPrefix,
                unselectedPrefix,
                footer
        );
    }

    private ConfigValues.ItemLoreConfig loadItemLoreConfig(ConfigurationSection auction) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection symbolSection = config.getConfigurationSection("symbol_value");
        ConfigurationSection itemLoreSection = symbolSection.getConfigurationSection("item-lore");
        List<String> lore = itemLoreSection.getStringList("lore");
        ConfigurationSection itemLoreOneSection = symbolSection.getConfigurationSection("item-lore_one");
        List<String> loreOne = itemLoreOneSection.getStringList("lore");
        ConfigurationSection buyLoreSection = symbolSection.getConfigurationSection("item-lore_buy_one_item");
        List<String> buyLore = buyLoreSection.getStringList("lore");
        return new ConfigValues.ItemLoreConfig(lore, loreOne, buyLore);
    }

    private ConfigValues.QuantityMenuConfig loadQuantityMenuConfig(ConfigurationSection auction) {
        ConfigurationSection qMenu = auction.getConfigurationSection("quantity-menu");

        int slotAmount = qMenu.getInt("slot-amount");
        int sizeMenu = qMenu.getInt("size-menu");

        ConfigValues.ButtonConfig decrease10 = loadButtonConfig(qMenu.getConfigurationSection("decrease-10"));
        ConfigValues.ButtonConfig decrease1 = loadButtonConfig(qMenu.getConfigurationSection("decrease-1"));
        ConfigValues.ButtonConfig amount = loadButtonConfig(qMenu.getConfigurationSection("amount"));
        ConfigValues.ButtonConfig increase1 = loadButtonConfig(qMenu.getConfigurationSection("increase-1"));
        ConfigValues.ButtonConfig increase10 = loadButtonConfig(qMenu.getConfigurationSection("increase-10"));

        Map<Integer, ConfigValues.GlassPane> glassPanes = loadGlassPanes(qMenu.getMapList("glass"));

        return new ConfigValues.QuantityMenuConfig(
                slotAmount,
                sizeMenu,
                decrease10,
                decrease1,
                amount,
                increase1,
                increase10,
                glassPanes
        );
    }

    private ConfigValues.NavigationConfig loadNavigationConfig(ConfigurationSection auction) {
        ConfigurationSection nav = auction.getConfigurationSection("navigation");
        ConfigurationSection previous = nav.getConfigurationSection("previous");
        ConfigurationSection refresh = nav.getConfigurationSection("refresh");
        ConfigurationSection next = nav.getConfigurationSection("next");
        ConfigurationSection selling = nav.getConfigurationSection("selling");
        ConfigurationSection expired = nav.getConfigurationSection("expired");
        ConfigurationSection sort = nav.getConfigurationSection("sort");
        ConfigurationSection categories = nav.getConfigurationSection("categories");
        return new ConfigValues.NavigationConfig(
                loadNavigationButton(previous),
                loadNavigationButton(refresh),
                loadNavigationButton(next),
                loadNavigationButton(selling),
                loadNavigationButton(expired),
                loadNavigationButton(sort),
                loadNavigationButton(categories)
        );
    }

    private ConfigValues.CategoryMenuConfig loadCategoryMenuConfig(ConfigurationSection auction) {
        ConfigurationSection section = auction.getConfigurationSection("category-menu");
        String name = section.getString("name");
        String selectedPrefix = section.getString("selected-prefix");
        String unselectedPrefix = section.getString("unselected-prefix");
        String footer = section.getString("footer");
        return new ConfigValues.CategoryMenuConfig(
                Material.matchMaterial(section.getString("material", "HOPPER")),
                name,
                selectedPrefix,
                unselectedPrefix,
                footer
        );
    }

    private ConfigValues.NavigationButton loadNavigationButton(ConfigurationSection section) {
        ParsedMaterial parsed = parseMaterialAndTexture(section);
        ActionType action = loadActionFromList(section);
        int slot = section.getInt("slot");
        String name = section.getString("name");
        List<String> lore = section.getStringList("lore");
        return new ConfigValues.NavigationButton(
                slot,
                parsed.material(),
                name,
                lore,
                parsed.skullTexture(),
                action
        );
    }

    private ConfigValues.ButtonConfig loadButtonConfig(ConfigurationSection section) {
        ParsedMaterial parsed = parseMaterialAndTexture(section);
        String name = section.getString("name", "");
        String title = section.getString("title", name);
        ActionType action = loadActionFromList(section);

        List<Integer> slots = section.getIntegerList("slots");
        if (slots.isEmpty()) {
            int singleSlot = section.getInt("slot", -1);
            if (singleSlot >= 0) {
                slots = List.of(singleSlot);
            }
        }

        return new ConfigValues.ButtonConfig(
                parsed.material(),
                title.isEmpty() ? name : title,
                List.copyOf(section.getStringList("lore")),
                parsed.skullTexture(),
                action,
                slots
        );
    }

    private ConfigValues.ConfigMessages loadMessagesConfig(FileConfiguration config) {
        ConfigurationSection messages = config.getConfigurationSection("messages");
        ConfigurationSection admin = messages.getConfigurationSection("admin");
        ConfigurationSection errors = messages.getConfigurationSection("errors");
        ConfigurationSection buy = messages.getConfigurationSection("buy");
        ConfigurationSection expired = messages.getConfigurationSection("expired");
        ConfigurationSection selling = messages.getConfigurationSection("selling");
        ConfigurationSection sell = config.getConfigurationSection("commands.sell");
        ConfigurationSection auctionErrors = config.getConfigurationSection("auction.errors");

        String reload = formatMessage(config, admin.getString("reload"));
        String unknownSubcommand = formatMessage(config, admin.getString("unknown-subcommand"));
        String errorReload = formatMessage(config, admin.getString("error-reload"));
        String noPermission = formatMessage(config, admin.getString("no-permission"));
        String noName = formatMessage(config, errors.getString("no-name"));
        String noId = formatMessage(config, errors.getString("no-id"));
        String noPrice = formatMessage(config, sell.getString("non-price"));
        String noOwn = formatMessage(config, errors.getString("no-own"));
        String economyUnavailable = formatMessage(config, auctionErrors.getString("economy-unavailable"));
        String air = formatMessage(config, sell.getString("air"));
        String sellSuccess = formatMessage(config, sell.getString("success"));
        String priceTooLow = formatMessage(config, sell.getString("price-too-low"));
        String priceTooHigh = formatMessage(config, sell.getString("price-too-high"));
        String limitReached = formatMessage(config, sell.getString("limit-reached"));
        String databaseError = formatMessage(config, sell.getString("database-error"));
        String enterPlayerName = formatMessage(config, String.join("\n", config.getStringList("search.enter-player-name")));
        String buySeller = formatMessage(config, buy.getString("buy-seller"));
        String otmena = formatMessage(config, buy.getString("otmena"));
        String yspex = formatMessage(config, buy.getString("yspex"));
        String noMoney = formatMessage(config, buy.getString("nomoney"));
        String quantityExceeded = formatMessage(config, buy.getString("quantity-exceeded"));
        String takeExpired = formatMessage(config, expired.getString("take"));
        String takeSelling = formatMessage(config, selling.getString("take"));
        String cooldown = formatMessage(config, messages.getString("cooldown"));
        String inventoryFull = formatMessage(config, messages.getString("inventory-full"));
        String cooldownItem = formatMessage(config, messages.getString("cooldown-item"));
        return new ConfigValues.ConfigMessages(
                reload,
                unknownSubcommand,
                errorReload,
                noPermission,
                noName,
                noId,
                noPrice,
                noOwn,
                economyUnavailable,
                air,
                sellSuccess,
                priceTooLow,
                priceTooHigh,
                limitReached,
                databaseError,
                enterPlayerName,
                buySeller,
                otmena,
                yspex,
                noMoney,
                quantityExceeded,
                takeExpired,
                takeSelling,
                cooldown,
                inventoryFull,
                cooldownItem
        );
    }

    private String formatMessage(FileConfiguration config, String message) {
        return TextUtils.colorize(message.replace("%prefix%", config.getString("prefix", "")));
    }

    private record ParsedMaterial(Material material, String skullTexture) {}

    private ParsedMaterial parseMaterialAndTexture(ConfigurationSection section) {
        String itemStr = section.getString("item", "");
        if (!itemStr.isEmpty()) {
            if (itemStr.startsWith("basehead-")) {
                return new ParsedMaterial(Material.PLAYER_HEAD, itemStr.substring("basehead-".length()));
            }
            Material mat = Material.matchMaterial(itemStr);
            return new ParsedMaterial(mat, null);
        }
        String materialStr = section.getString("material", "");
        Material mat = Material.matchMaterial(materialStr);
        return new ParsedMaterial(mat, null);
    }

    private ActionType loadActionFromList(ConfigurationSection section) {
        List<String> actions = section.getStringList("actions");
        if (actions.isEmpty()) {
            return null;
        }
        String first = actions.get(0).trim();
        if (first.startsWith("[") && first.endsWith("]")) {
            return ActionType.get(first.substring(1, first.length() - 1));
        }
        return ActionType.get(first);
    }

    private void loadItemsYml() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }
        ItemUtils.loadTranslations(itemsFile);
    }
}
