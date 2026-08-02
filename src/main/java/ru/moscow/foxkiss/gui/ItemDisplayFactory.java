package ru.moscow.foxkiss.gui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.text.Component;
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
import ru.moscow.foxkiss.utils.TextUtils;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.*;

public final class ItemDisplayFactory {

    private final IConfigManager configManager;
    private final JavaPlugin plugin;

    private final LinkedHashMap<String, ItemStack> navCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > 128;
        }
    };

    public ItemDisplayFactory(JavaPlugin plugin, IConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public ItemStack createLotDisplay(AuctionItem item) {
        ItemStack base = item.itemStackClone();
        ItemMeta meta = base.getItemMeta();

        ObjectArrayList<Component> lore = new ObjectArrayList<>();

        if (meta != null && meta.hasLore()) {
            List<Component> originalLore = meta.lore();
            if (originalLore != null) {
                lore.addAll(originalLore);
            }
        }

        ConfigValues config = configManager.getConfigValues();
        ConfigValues.ItemLoreConfig loreConfig = config.guiConfig().itemLore();

        List<String> template = (item.amount() == 1)
                ? loreConfig.loreOne()
                : loreConfig.lore();

        String symbol = item.currency().symbol(config);
        String price = PriceFormatter.format(item.price());
        String pricePerItem = PriceFormatter.format(item.pricePerItem());
        String amount = String.valueOf(item.amount());
        long daysLeft = Math.max(config.maxAuctionStorageDays() - (System.currentTimeMillis() - item.createdAt()) / 86_400_000L, 0);

        for (String line : template) {
            String processed = line
                    .replace("{price}", price)
                    .replace("{pricePerItem}", pricePerItem)
                    .replace("{seller}", item.sellerName())
                    .replace("{amount}", amount)
                    .replace("{daysLeft}", String.valueOf(daysLeft))
                    .replace("{symbol_value}", symbol)
                    .replace("{listedDate}", formatDate(item.createdAt()));
            lore.add(TextUtils.component(processed));
        }

        if (meta != null) {
            meta.lore(lore);
            base.setItemMeta(meta);
        }

        return base;
    }

    public void clearCache() {
        navCache.clear();
    }

    private ItemStack createButton(Material material, String name, List<String> lore, String skullTexture, ActionType action, Integer customModelData) {
        ItemStack item = TextUtils.isNotBlank(skullTexture)
                ? ItemUtils.skull(skullTexture, name, lore, customModelData)
                : ItemUtils.named(material, name, lore, customModelData);

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            setAction(meta, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createButton(ConfigValues.ButtonConfig config) {
        return createButton(config.material(), config.name(), config.lore(), config.skullTexture(), config.action(), config.customModelData());
    }

    public ItemStack createButton(ConfigValues.ConfirmButtonConfig config) {
        return createButton(config.material(), config.name(), config.lore(), config.skullTexture(), config.action(), config.customModelData());
    }

    public ItemStack createNavigationButton(ConfigValues.NavigationButton button, List<String> lore) {
        String key = button.slot() + "|" + button.name() + "|" + String.join("", lore) + "|" + button.customModelData();
        return navCache.computeIfAbsent(key, k -> createButton(button.material(), button.name(), lore, button.skullTexture(), button.action(), button.customModelData())).clone();
    }

    public ItemStack createSortButton(AuctionSort selected) {
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.SortMenuConfig sortMenu = config.guiConfig().sortMenu();
        ConfigValues.NavigationConfig nav = config.guiConfig().navigation();

        ObjectArrayList<String> lore = new ObjectArrayList<>();
        for (AuctionSort sort : AuctionSort.values()) {
            String display = config.sortingNames().getOrDefault(sort.name(), sort.name());
            String prefix = (sort == selected)
                    ? sortMenu.selectedPrefix()
                    : sortMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(sortMenu.footer()));

        return createNavigationButton(nav.sort(), lore);
    }

    public ItemStack createCategoryButton(String selectedCategory) {
        ConfigValues config = configManager.getConfigValues();
        ConfigValues.CategoryMenuConfig categoryMenu = config.guiConfig().categoryMenu();
        ConfigValues.NavigationConfig nav = config.guiConfig().navigation();

        ObjectArrayList<String> categories = new ObjectArrayList<>(config.categories().keySet());
        if (!categories.contains("all")) {
            categories.addFirst("all");
        }

        String current = selectedCategory.toLowerCase();

        ObjectArrayList<String> lore = new ObjectArrayList<>();
        for (String category : categories) {
            String display = config.categoryNames().getOrDefault(category.toLowerCase(), category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase());
            String prefix = category.equalsIgnoreCase(current)
                    ? categoryMenu.selectedPrefix()
                    : categoryMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(categoryMenu.footer()));

        return createNavigationButton(nav.categories(), lore);
    }

    public ItemStack createBuyItem(AuctionItem item, int selectedAmount) {
        ItemStack display = item.itemStackClone();
        display.setAmount(Math.min(selectedAmount, display.getMaxStackSize()));

        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON);

        ObjectArrayList<Component> lore = new ObjectArrayList<>();

        if (meta.hasLore()) {
            List<Component> originalLore = meta.lore();
            if (originalLore != null) {
                lore.addAll(originalLore);
                lore.add(Component.empty());
            }
        }
        
        double totalPrice = item.pricePerItem() * selectedAmount;
        String totalPriceFormatted = PriceFormatter.format(totalPrice);

        for (String line : configManager.getConfigValues().guiConfig().itemLore().buyLore()) {
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

    private void setAction(ItemMeta meta, ActionType action) {
        if (meta == null) return;
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, action.name());
    }

    private String formatDate(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / 60_000L;
        if (minutes < 60) return minutes + " мин. назад";
        long hours = diff / 3_600_000L;
        if (hours < 24) return hours + " ч. назад";
        return diff / 86_400_000L + " дн. назад";
    }
}