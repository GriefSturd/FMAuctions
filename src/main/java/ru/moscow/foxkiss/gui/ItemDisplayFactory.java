package ru.moscow.foxkiss.gui;

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
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.*;

@RequiredArgsConstructor
public final class ItemDisplayFactory {
    private final IConfigManager configManager;
    private final NamespacedKey actionKey;

    private final Map<String, ItemStack> lotDisplayCache = new HashMap<>();
    private final Map<String, ItemStack> navCache = new HashMap<>();
    private final Map<AuctionSort, ItemStack> sortButtonCache = new HashMap<>();
    private final Map<String, ItemStack> categoryButtonCache = new HashMap<>();

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

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) {
            List<Component> original = meta.lore();
            if (original != null) lore.addAll(original);
        }

        ConfigValues config = configManager.getConfigValues();
        ConfigValues.ItemLoreConfig loreConfig = config.guiConfig().itemLore();
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

    public ItemStack createButton(ConfigValues.ButtonConfig config) {
        return createButtonInternal(config.material(), config.name(), config.lore(), config.skullTexture(), config.action(), config.customModelData());
    }

    public ItemStack createButton(ConfigValues.ConfirmButtonConfig config) {
        return createButtonInternal(config.material(), config.name(), config.lore(), config.skullTexture(), config.action(), config.customModelData());
    }

    public ItemStack createNavigationButton(ConfigValues.NavigationButton button, List<String> lore) {
        String key = button.slot() + "|" + button.name() + "|" + String.join("", lore) + "|" + button.customModelData();
        return navCache.computeIfAbsent(key, k -> createButtonInternal(button.material(), button.name(), lore, button.skullTexture(), button.action(), button.customModelData())).clone();
    }

    public ItemStack createSortButton(AuctionSort selected) {
        return sortButtonCache.computeIfAbsent(selected, this::buildSortButton).clone();
    }

    private ItemStack buildSortButton(AuctionSort selected) {
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.SortMenuConfig sortMenu = config.guiConfig().sortMenu();
        ConfigValues.NavigationConfig nav = config.guiConfig().navigation();

        List<String> lore = new ArrayList<>();
        Map<String, String> sortingNames = config.sortingNames();
        for (AuctionSort sort : AuctionSort.values()) {
            String display = sortingNames.getOrDefault(sort.name(), sort.name());
            String prefix = (sort == selected) ? sortMenu.selectedPrefix() : sortMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(sortMenu.footer()));

        return createNavigationButton(nav.sort(), lore);
    }

    public ItemStack createCategoryButton(String selectedCategory) {
        if (selectedCategory == null) {
            selectedCategory = "all";
        }
        String key = selectedCategory.toLowerCase();
        return categoryButtonCache.computeIfAbsent(key, this::buildCategoryButton).clone();
    }

    private ItemStack buildCategoryButton(String selectedCategory) {
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.CategoryMenuConfig categoryMenu = config.guiConfig().categoryMenu();
        ConfigValues.NavigationConfig nav = config.guiConfig().navigation();

        List<String> categories = new ArrayList<>(config.categories().keySet());
        if (!categories.contains("all")) {
            categories.addFirst("all");
        }

        String current = selectedCategory.toLowerCase();
        List<String> lore = new ArrayList<>();
        Map<String, String> categoryNames = config.categoryNames();
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
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) {
            List<Component> original = meta.lore();
            if (original != null) lore.addAll(original);
        }
        if (!lore.isEmpty()) lore.add(Component.empty());

        double totalPrice = item.pricePerItem() * selectedAmount;
        String totalPriceFormatted = PriceFormatter.format(totalPrice);

        for (String line : configManager.getConfigValues().guiConfig().itemLore().buyLore()) {
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

    private ItemStack createButtonInternal(Material material, String name, List<String> lore, String skullTexture, ActionType action, Integer customModelData) {
        if (material == null) {
            material = Material.STONE;
            Bukkit.getLogger().warning("ItemDisplayFactory: материал null, заменён на STONE для кнопки " + name);
        }
        ItemStack item = TextUtils.isNotBlank(skullTexture)
                ? ItemUtils.skull(skullTexture, name, lore, customModelData)
                : ItemUtils.named(material, name, lore, customModelData);

        ItemMeta meta = item.getItemMeta();
        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.name());
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