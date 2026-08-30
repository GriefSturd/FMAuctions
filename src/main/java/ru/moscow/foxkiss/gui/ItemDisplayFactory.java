package ru.moscow.foxkiss.gui;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.gui.actions.Action;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.*;

@RequiredArgsConstructor
public final class ItemDisplayFactory {
    private final IConfigManager configManager;
    private final NamespacedKey actionKey;

    private final Object2ObjectOpenHashMap<String, ItemStack> lotDisplayCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, ItemStack> navCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, ItemStack> sortButtonCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, ItemStack> categoryButtonCache = new Object2ObjectOpenHashMap<>();

    public ItemDisplayFactory(JavaPlugin plugin, IConfigManager configManager) {
        this.configManager = configManager;
        this.actionKey = new NamespacedKey(plugin, "action");
    }

    public ItemStack createLotDisplay(AuctionItem item) {
        String key = item.getId() + "|" + item.amount() + "|" + item.getPrice() + "|" + item.getCreatedAt();
        return lotDisplayCache.computeIfAbsent(key, k -> buildLotDisplay(item)).clone();
    }

    private ItemStack buildLotDisplay(AuctionItem item) {
        ItemStack base = item.getItemStack().clone();
        ItemMeta meta = base.getItemMeta();

        if (!meta.hasDisplayName()) {
            String translated = ItemUtils.getTranslation(base.getType());
            if (translated != null && !translated.isBlank()) {
                meta.displayName(TextUtils.component(TextUtils.colorize(translated)));
            }
        }

        ObjectList<Component> lore = new ObjectArrayList<>();
        if (meta.hasLore()) {
            List<Component> original = meta.lore();
            if (original != null) lore.addAll(original);
        }

        ConfigValues config = configManager.getConfigValues();
        ConfigValues.AuctionGuiConfig gui = config.gui(item.getCurrency());
        ConfigValues.ItemLoreConfig loreConfig = gui.itemLore();
        List<String> template = (item.amount() == 1) ? loreConfig.loreOne() : loreConfig.lore();

        String symbol = item.getCurrency().symbol(config);
        String price = PriceFormatter.format(item.getPrice());
        String pricePerItem = PriceFormatter.format(item.pricePerItem());
        String amount = String.valueOf(item.amount());
        long daysLeft = Math.max(config.maxAuctionStorageDays() - (System.currentTimeMillis() - item.getCreatedAt()) / 86_400_000L, 0);
        String daysLeftText = String.valueOf(daysLeft);
        String listedDate = formatDate(item.getCreatedAt());

        for (String line : template) {
            String processed = line
                    .replace("{price}", price)
                    .replace("{pricePerItem}", pricePerItem)
                    .replace("{seller}", item.getSellerName())
                    .replace("{amount}", amount)
                    .replace("{daysLeft}", daysLeftText)
                    .replace("{symbol_value}", symbol)
                    .replace("{listedDate}", listedDate);
            lore.add(TextUtils.component(processed));
        }

        meta.lore(lore);
        base.setItemMeta(meta);
        return base;
    }

    public void invalidateLotCache(long lotId) {
        lotDisplayCache.entrySet().removeIf(entry -> entry.getKey().startsWith(lotId + "|"));
    }

    public ItemStack createInventoryDisplay(AuctionItem item) {
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.InventorySellingConfig inv = config.inventorySelling();

        Material shulker = item.getItemStack().getType();
        if (shulker == null || shulker == Material.AIR) {
            shulker = Material.WHITE_SHULKER_BOX;
        }

        String symbol = item.getCurrency().symbol(config);
        String price = PriceFormatter.format(item.getPrice());

        String name = TextUtils.colorize((inv.displayName() == null ? "Инвентарь игрока %player%" : inv.displayName())
                .replace("%player%", item.getSellerName()));

        ObjectList<String> lore = new ObjectArrayList<>();
        for (String line : inv.displayLore()) {
            lore.add(TextUtils.colorize(line
                    .replace("%player%", item.getSellerName())
                    .replace("%price%", symbol + price)));
        }

        ItemStack box = ItemUtils.named(shulker, name, lore, null);
        ItemMeta meta = box.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtils.component(name));
            box.setItemMeta(meta);
        }
        box.setAmount(Math.max(1, Math.min(item.amount(), box.getMaxStackSize())));
        return box;
    }

    public ItemStack createInventoryItemDisplay(ItemStack source) {
        ItemStack display = source.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            String translated = ItemUtils.getTranslation(display.getType());
            if (translated != null && !translated.isBlank()) {
                meta.displayName(TextUtils.component(TextUtils.colorize(translated)));
            }
        }
        display.setItemMeta(meta);
        return display;
    }

    public ItemStack createConfigButton(Material material, String name, List<String> lore, List<Action> actions) {
        return createButtonInternal(material, name, lore, null, actions, null);
    }

    public ItemStack createButton(ConfigValues.ButtonConfig config) {
        return createButtonInternal(config.material(), config.name(), config.lore(), config.skullTexture(), config.actions(), config.customModelData());
    }

    public ItemStack createButton(ConfigValues.ConfirmButtonConfig config) {
        return createButtonInternal(config.material(), config.name(), config.lore(), config.skullTexture(), config.actions(), config.customModelData());
    }

    public ItemStack createNavigationButton(ConfigValues.NavigationButton button, List<String> lore) {
        String key = button.slot() + "|" + button.name() + "|" + String.join("", lore) + "|" + button.customModelData();
        return navCache.computeIfAbsent(key, k -> createButtonInternal(button.material(), button.name(), lore, button.skullTexture(), button.actions(), button.customModelData())).clone();
    }

    public ItemStack createSortButton(AuctionSort selected, AuctionCurrency currency) {
        String key = selected.name() + "|" + currency.name();
        return sortButtonCache.computeIfAbsent(key, k -> buildSortButton(selected, currency)).clone();
    }

    private ItemStack buildSortButton(AuctionSort selected, AuctionCurrency currency) {
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.AuctionGuiConfig gui = config.gui(currency);
        ConfigValues.SortMenuConfig sortMenu = gui.sortMenu();
        ConfigValues.NavigationConfig nav = gui.navigation();

        ObjectList<String> lore = new ObjectArrayList<>();
        Map<String, String> sortingNames = gui.sortingNames();
        for (AuctionSort sort : AuctionSort.values()) {
            String display = sortingNames.getOrDefault(sort.name(), sort.name());
            String prefix = (sort == selected) ? sortMenu.selectedPrefix() : sortMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(sortMenu.footer()));

        return createNavigationButton(nav.sort(), lore);
    }

    public ItemStack createCategoryButton(String selectedCategory, AuctionCurrency currency) {
        final String cat = selectedCategory == null ? "all" : selectedCategory;
        String key = cat.toLowerCase() + "|" + currency.name();
        return categoryButtonCache.computeIfAbsent(key, k -> buildCategoryButton(cat, currency)).clone();
    }

    private ItemStack buildCategoryButton(String selectedCategory, AuctionCurrency currency) {
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.AuctionGuiConfig gui = config.gui(currency);
        ConfigValues.CategoryMenuConfig categoryMenu = gui.categoryMenu();
        ConfigValues.NavigationConfig nav = gui.navigation();

        ObjectList<String> categories = new ObjectArrayList<>(config.categories().keySet());
        if (!categories.contains("all")) {
            categories.add(0, "all");
        }

        String current = selectedCategory.toLowerCase();
        ObjectList<String> lore = new ObjectArrayList<>();
        Map<String, String> categoryNames = gui.categoryNames();
        for (String category : categories) {
            String display = categoryNames.getOrDefault(category.toLowerCase(), category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase());
            String prefix = category.equalsIgnoreCase(current) ? categoryMenu.selectedPrefix() : categoryMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(categoryMenu.footer()));

        return createNavigationButton(nav.categories(), lore);
    }

    public ItemStack createBuyItem(AuctionItem item, int selectedAmount) {
        ItemStack display = item.getItemStack().clone();
        display.setAmount(Math.min(selectedAmount, display.getMaxStackSize()));

        ItemMeta meta = display.getItemMeta();
        if (!meta.hasDisplayName()) {
            String translated = ItemUtils.getTranslation(display.getType());
            if (translated != null && !translated.isBlank()) {
                meta.displayName(TextUtils.component(TextUtils.colorize(translated)));
            }
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);

        ObjectList<Component> lore = new ObjectArrayList<>();
        if (meta.hasLore()) {
            List<Component> original = meta.lore();
            if (original != null) lore.addAll(original);
        }
        if (!lore.isEmpty()) lore.add(Component.empty());

        double totalPrice = item.pricePerItem() * selectedAmount;
        String totalPriceFormatted = PriceFormatter.format(totalPrice);

        for (String line : configManager.getConfigValues().gui(item.getCurrency()).itemLore().buyLore()) {
            String processed = line
                    .replace("{total_price}", totalPriceFormatted)
                    .replace("{seller}", item.getSellerName())
                    .replace("{amount}", String.valueOf(selectedAmount));
            lore.add(TextUtils.component(processed));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createButtonInternal(Material material, String name, List<String> lore, String skullTexture, List<Action> actions, Integer customModelData) {
        if (material == null) {
            material = Material.STONE;
            Bukkit.getLogger().warning("ItemDisplayFactory: материал null, заменён на STONE для кнопки " + name);
        }
        ItemStack item = TextUtils.isNotBlank(skullTexture)
                ? ItemUtils.skull(skullTexture, name, lore, customModelData)
                : ItemUtils.named(material, name, lore, customModelData);

        ItemMeta meta = item.getItemMeta();
        if (actions != null && !actions.isEmpty()) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actions.get(0).type().name());
        }
        item.setItemMeta(meta);
        return item;
    }

    private String formatDate(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / 60_000L;
        if (minutes < 60) return minutes + " мин. назад";
        long hours = diff / 3_600_000L;
        if (hours < 24) return hours + " ч. назад";
        return diff / 86_400_000L + " дн. назад";
    }

    public void clearCache() {
        lotDisplayCache.clear();
        navCache.clear();
        sortButtonCache.clear();
        categoryButtonCache.clear();
    }
}