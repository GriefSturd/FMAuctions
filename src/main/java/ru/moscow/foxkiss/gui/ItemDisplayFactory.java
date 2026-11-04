package ru.moscow.foxkiss.gui;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.auction.PlayerProfile;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.*;

public final class ItemDisplayFactory {
    private final IConfigManager configManager;

    private final Object2ObjectOpenHashMap<String, ItemStack> lotDisplayCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, ItemStack> navCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, ItemStack> sortButtonCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, ItemStack> categoryButtonCache = new Object2ObjectOpenHashMap<>();

    public ItemDisplayFactory(IConfigManager configManager) {
        this.configManager = configManager;
    }

    public ItemStack createLotDisplay(AuctionItem item, PlayerProfile profile) {
        long now = System.currentTimeMillis();
        int maxStorageDays = configManager.getConfigValues().maxAuctionStorageDays();
        long ageBucket = LotDisplayTime.ageBucket(item.createdAt(), now);
        long daysLeft = LotDisplayTime.daysLeft(item.createdAt(), now, maxStorageDays);

        String key = item.id()
                + "|" + item.amount()
                + "|" + item.price()
                + "|" + item.createdAt()
                + "|" + item.status()
                + "|" + item.itemStack().hashCode()
                + "|" + profile.discord()
                + "|" + profile.storeName()
                + "|" + profile.sales()
                + "|" + ageBucket
                + "|" + daysLeft;

        return lotDisplayCache.computeIfAbsent(key, k -> buildLotDisplay(item, profile)).clone();
    }


    private ItemStack buildLotDisplay(AuctionItem item, PlayerProfile profile) {
        ItemStack base = item.itemStack().clone();
        ItemMeta meta = base.getItemMeta();

        if (!meta.hasDisplayName()) {
            String translated = ItemUtils.getTranslation(base.getType());
            if (translated != null && !translated.isBlank()) {
                meta.displayName(TextUtils.component(TextUtils.colorize(translated)));
            }
        }

        ObjectList<Component> lore = new ObjectArrayList<>();
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.AuctionGuiConfig gui = config.gui(item.currency());
        ConfigValues.ItemLoreConfig loreConfig = gui.itemLore();
        
        boolean hasProfile = !profile.discord().isEmpty() || !profile.storeName().isEmpty();
        
        if (!hasProfile && meta.hasLore()) {
            List<Component> original = meta.lore();
            if (original != null) lore.addAll(original);
        }
        
        List<String> template = loreTemplate(item, profile, loreConfig);

        String symbol = item.currency().symbol(config);
        String price = PriceFormatter.format(item.price());
        String pricePerItem = PriceFormatter.format(item.pricePerItem());
        String amount = String.valueOf(item.amount());
        long now = System.currentTimeMillis();
        long ageBucket = LotDisplayTime.ageBucket(item.createdAt(), now);
        long daysLeft = LotDisplayTime.daysLeft(item.createdAt(), now, config.maxAuctionStorageDays());

        String daysLeftText = String.valueOf(daysLeft);
        String listedDate = LotDisplayTime.formatAge(ageBucket);

        for (String line : template) {
            String processed = line
                    .replace("{price}", price)
                    .replace("{pricePerItem}", pricePerItem)
                    .replace("{seller}", item.sellerName())
                    .replace("{amount}", amount)
                    .replace("{daysLeft}", daysLeftText)
                    .replace("{symbol_value}", symbol)
                    .replace("{listedDate}", listedDate);
            processed = processed.replace("{discord}", profile.discord()).replace("{store}", profile.storeName()).replace("{sales}", String.valueOf(profile.sales()));
            lore.add(TextUtils.component(processed));
        }

        meta.lore(lore);
        base.setItemMeta(meta);
        return base;
    }

    private List<String> loreTemplate(AuctionItem item, PlayerProfile profile, ConfigValues.ItemLoreConfig config) {
        boolean discord = !profile.discord().isEmpty();
        boolean store = !profile.storeName().isEmpty();

        if (discord && store) return item.amount() == 1 ? config.discordStoreLoreOne() : config.discordStoreLore();
        if (discord) return item.amount() == 1 ? config.discordLoreOne() : config.discordLore();
        if (store) return item.amount() == 1 ? config.storeLoreOne() : config.storeLore();
        return item.amount() == 1 ? config.loreOne() : config.lore();
    }

    public void invalidateLotCache(long lotId) {
        lotDisplayCache.entrySet().removeIf(entry -> entry.getKey().startsWith(lotId + "|"));
    }

    public ItemStack createInventoryDisplay(AuctionItem item) {
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.InventorySellingConfig inv = config.inventorySelling();

        Material shulker = item.itemStack().getType();
        if (shulker == Material.AIR) {
            shulker = Material.WHITE_SHULKER_BOX;
        }

        String symbol = item.currency().symbol(config);
        String price = PriceFormatter.format(item.price());

        String name = TextUtils.colorize((inv.displayName() == null ? "Инвентарь игрока %player%" : inv.displayName())
                .replace("%player%", item.sellerName()));

        ObjectList<String> lore = new ObjectArrayList<>();
        for (String line : inv.displayLore()) {
            lore.add(TextUtils.colorize(line
                    .replace("%player%", item.sellerName())
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

    public ItemStack createConfigButton(Material material, String name, List<String> lore) {
        return createButtonInternal(material, name, lore, null, null);
    }

    public ItemStack createButton(ConfigValues.ButtonConfig config) {
        return createButtonInternal(config.material(), config.name(), config.lore(), config.skullTexture(), config.customModelData());
    }

    public ItemStack createButton(ConfigValues.ConfirmButtonConfig config) {
        return createButtonInternal(config.material(), config.name(), config.lore(), config.skullTexture(), config.customModelData());
    }

    public ItemStack createNavigationButton(ConfigValues.NavigationButton button, List<String> lore) {
        String key = button.slot() + "|" + button.name() + "|" + String.join("", lore) + "|" + button.customModelData();
        return navCache.computeIfAbsent(key, k -> createButtonInternal(button.material(), button.name(), lore, button.skullTexture(), button.customModelData())).clone();
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
        if (!nav.sort().lore().isEmpty()) {
            for (String line : nav.sort().lore()) {
                for (AuctionSort sort : AuctionSort.values()) {
                    line = line.replace("{sort:" + sort.name() + "}", sort == selected ? sortMenu.selectedPrefix() : sortMenu.unselectedPrefix());
                }
                lore.add(line);
            }
            return createNavigationButton(nav.sort(), lore);
        }
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

        String current = selectedCategory.toLowerCase();
        ObjectList<String> lore = new ObjectArrayList<>();
        if (categoryMenu.entries() != null) {
            for (ConfigValues.CategoryEntry entry : categoryMenu.entries()) {
                lore.add(entry.id().equals(current) ? entry.selected() : entry.unselected());
            }
        } else {
            ObjectList<String> categories = new ObjectArrayList<>(config.categories().keySet());
            if (!categories.contains("all")) categories.addFirst("all");
            Map<String, String> categoryNames = gui.categoryNames();
            for (String category : categories) {
                String display = categoryNames.getOrDefault(category, category);
                String prefix = category.equalsIgnoreCase(current) ? categoryMenu.selectedPrefix() : categoryMenu.unselectedPrefix();
                lore.add(prefix + display);
            }
        }
        if (!nav.categories().lore().isEmpty()) return createNavigationButton(nav.categories(), lore);
        lore.add("");
        lore.add(TextUtils.colorize(categoryMenu.footer()));

        return createNavigationButton(nav.categories(), lore);
    }

    public ItemStack createBuyItem(AuctionItem item, int selectedAmount) {
        ItemStack display = item.itemStack().clone();
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

        double totalPrice = item.calculatePurchasePrice(selectedAmount);
        String totalPriceFormatted = PriceFormatter.format(totalPrice, item.currency());

        for (String line : configManager.getConfigValues().gui(item.currency()).itemLore().buyLore()) {
            String processed = line
                    .replace("{total_price}", totalPriceFormatted)
                    .replace("{seller}", item.sellerName())
                    .replace("{amount}", String.valueOf(selectedAmount));
            lore.add(TextUtils.component(processed));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createButtonInternal(Material material, String name, List<String> lore, String skullTexture, Integer customModelData) {
        ItemStack item = TextUtils.isNotBlank(skullTexture)
                ? ItemUtils.skull(skullTexture, name, lore, customModelData)
                : ItemUtils.named(material, name, lore, customModelData);

        ItemMeta meta = item.getItemMeta();

        item.setItemMeta(meta);
        return item;
    }

    public void clearCache() {
        lotDisplayCache.clear();
        navCache.clear();
        sortButtonCache.clear();
        categoryButtonCache.clear();
    }
}
