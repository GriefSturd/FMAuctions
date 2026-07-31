package ru.moscow.foxkiss.gui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.utils.CacheManager;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.TextUtils;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemFlag;

import java.util.*;

public final class ItemDisplayFactory {

    private final IConfigManager configManager;
    private final JavaPlugin plugin;
    private final Map<NavigationButtonKey, ItemStack> navigationButtonCache = new HashMap<>();
    private final Map<Long, ItemStack> lotDisplayCache = new HashMap<>();

    public ItemDisplayFactory(JavaPlugin plugin, IConfigManager configManager, CacheManager cacheManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        cacheManager.registerClearTask(() -> {
            lotDisplayCache.clear();
            navigationButtonCache.clear();
        });
    }

    public ItemStack createLotDisplay(AuctionItem item) {
        Long id = item.id();
        ItemStack cached = lotDisplayCache.get(id);
        if (cached != null) return cached.clone();

        ConfigValues values = configManager.getConfigValues();
        ConfigValues.ItemLoreConfig loreConfig = values.guiConfig().itemLore();
        ItemStack base = item.itemStackClone();
        ItemMeta meta = base.getItemMeta();
        if (meta == null) return base;

        List<Component> lore = meta.lore() != null
                ? new ArrayList<>(meta.lore())
                : new ArrayList<>();

        String symbol = item.currency().symbol(values);
        String price = PriceFormatter.format(item.price());
        String pricePerItem = PriceFormatter.format(item.pricePerItem());
        long daysLeft = values.maxAuctionStorageDays() - ((System.currentTimeMillis() - item.createdAt()) / 86_400_000L);
        String amount = String.valueOf(item.amount());

        List<String> template = item.amount() == 1 ? loreConfig.loreOne() : loreConfig.lore();
        for (String line : template) {
            String processed = line
                    .replace("{price}", price)
                    .replace("{pricePerItem}", pricePerItem)
                    .replace("{seller}", item.sellerName())
                    .replace("{amount}", amount)
                    .replace("{daysLeft}", String.valueOf(Math.max(daysLeft, 0)))
                    .replace("{symbol_value}", symbol)
                    .replace("{listedDate}", formatDate(item.createdAt()));
            lore.add(TextUtils.component(processed));
        }

        meta.lore(lore);
        base.setItemMeta(meta);
        lotDisplayCache.put(id, base.clone());
        return base;
    }

    public void clearLotDisplayCache() {
        lotDisplayCache.clear();
        navigationButtonCache.clear();
    }

    private ItemStack createButton(String skullTexture, Material material, String name, List<String> lore, ActionType action) {
        String texture = skullTexture != null ? skullTexture : "";
        ItemStack item = !texture.isEmpty()
                ? ItemUtils.skull(texture, name, lore)
                : ItemUtils.named(material, name, lore);

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            setAction(meta, action);
            item.setItemMeta(meta);
        }

        return item;
    }

    public ItemStack createButton(ConfigValues.ButtonConfig config) {
        return createButton(config.skullTexture(), config.material(), config.name(), config.lore(), config.action());
    }

    public ItemStack createButton(ConfigValues.ConfirmButtonConfig config) {
        return createButton(config.skullTexture(), config.material(), config.name(), config.lore(), config.action());
    }


    public ItemStack createBuyItem(AuctionItem item, int selectedAmount) {
        ItemStack display = item.itemStackClone();
        display.setAmount(Math.min(selectedAmount, display.getMaxStackSize()));
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON);

        List<Component> lore = new ArrayList<>();
        double totalPrice = item.pricePerItem() * selectedAmount;

        String totalPriceFormatted = PriceFormatter.format(totalPrice);
        String amount = String.valueOf(selectedAmount);

        for (String line : configManager.getConfigValues().guiConfig().itemLore().buyLore()) {
            lore.add(TextUtils.component(line
                    .replace("{total_price}", totalPriceFormatted)
                    .replace("{seller}", item.sellerName())
                    .replace("{amount}", amount)));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public ItemStack createNavigationButton(ConfigValues.NavigationButton button, List<String> lore) {
        NavigationButtonKey cacheKey = new NavigationButtonKey(button, List.copyOf(lore));

        ItemStack cached = navigationButtonCache.get(cacheKey);
        if (cached != null) return cached.clone();

        String skullTexture = button.skullTexture();
        String texture = skullTexture != null ? skullTexture : "";
        ItemStack item = !texture.isEmpty()
                ? ItemUtils.skull(texture, button.name(), lore)
                : ItemUtils.named(button.material(), button.name(), lore);

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            setAction(meta, button.action());
            item.setItemMeta(meta);
        }

        navigationButtonCache.put(cacheKey, item.clone());
        return item;
    }

    public ItemStack createSortButton(AuctionSort selected) {
        ConfigValues values = configManager.getConfigValues();
        ConfigValues.SortMenuConfig sortMenu = values.guiConfig().sortMenu();
        ConfigValues.NavigationConfig nav = values.guiConfig().navigation();

        List<String> lore = new ArrayList<>();
        for (AuctionSort sort : AuctionSort.values()) {
            String display = values.sortingNames().getOrDefault(sort.name(), sort.name());
            String prefix = (sort == selected) ? sortMenu.selectedPrefix() : sortMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(sortMenu.footer()));

        return createNavigationButton(nav.sort(), lore);
    }

    public void invalidateLotDisplay(long lotId) {
        lotDisplayCache.remove(lotId);
    }

    public ItemStack createCategoryButton(String selectedCategory) {
        ConfigValues values = configManager.getConfigValues();
        ConfigValues.CategoryMenuConfig categoryMenu = values.guiConfig().categoryMenu();
        ConfigValues.NavigationConfig nav = values.guiConfig().navigation();

        List<String> categories = new ArrayList<>(values.categories().keySet());
        if (!categories.contains("all")) categories.add(0, "all");

        String current = (selectedCategory == null || selectedCategory.isEmpty()) ? "all" : selectedCategory.toLowerCase();

        List<String> lore = new ArrayList<>();
        for (String category : categories) {
            String display = values.categoryNames().getOrDefault(category.toLowerCase(), category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase());
            String prefix = category.equalsIgnoreCase(current) ? categoryMenu.selectedPrefix() : categoryMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(categoryMenu.footer()));

        return createNavigationButton(nav.categories(), lore);
    }

    private void setAction(ItemMeta meta, ActionType action) {
        if (meta == null || action == null) return;
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "action"),
                PersistentDataType.STRING,
                action.name()
        );
    }

    private String formatDate(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / 60_000L;
        if (minutes < 60) return minutes + " мин. назад";
        long hours = diff / 3_600_000L;
        if (hours < 24) return hours + " ч. назад";
        return diff / 86_400_000L + " дн. назад";
    }

    private record NavigationButtonKey(ConfigValues.NavigationButton button, List<String> lore) {}
}