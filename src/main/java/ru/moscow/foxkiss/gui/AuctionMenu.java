package ru.moscow.foxkiss.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.TextUtils;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AuctionMenu {

    private static final String GLASS_SELLING = "selling";
    private static final String GLASS_EXPIRED = "expired";
    private static final String GLASS_VAULT = "vault";
    private static final String GLASS_PLAYERPOINTS = "playerpoints";

    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final FMAuction plugin;
    private final PlayerPreferences playerPreferences;
    private final NamespacedKey actionKey;

    public AuctionMenu(FMAuction plugin, IConfigManager configManager, AuctionRepository repository, PlayerPreferences playerPreferences) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.playerPreferences = playerPreferences;
        this.actionKey = new NamespacedKey(plugin, "action");
    }

    public void openMain(Player player, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        if (sort == null) {
            sort = playerPreferences.getSort(player.getUniqueId(), currency);
        }
        if (category == null || category.isEmpty()) {
            category = playerPreferences.getCategory(player.getUniqueId(), currency);
        }
        playerPreferences.setSort(player.getUniqueId(), currency, sort);
        playerPreferences.setCategory(player.getUniqueId(), currency, category);
        openInventory(player, AuctionViewType.MAIN, currency, page, sort, sellerFilter, searchFilter, category);
    }

    public void openSelling(Player player, AuctionCurrency currency, int page) {
        openInventory(player, AuctionViewType.SELLING, currency, page, AuctionSort.NEWEST, null, null, null);
    }

    public void openExpired(Player player, AuctionCurrency currency, int page) {
        openInventory(player, AuctionViewType.EXPIRED, currency, page, AuctionSort.NEWEST, null, null, null);
    }

    public void openQuantityAsync(Player player, AuctionCurrency currency, long lotId) {
        repository.findById(lotId).thenAccept(item -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (item.isEmpty()) {
                    player.sendMessage("§cТовар уже купили или убрали с аукциона.");
                    return;
                }
                openQuantity(player, currency, item.get(), 1);
            });
        });
    }

    public void openQuantity(Player player, AuctionCurrency currency, AuctionItem auctionItem, int selectedAmount) {
        ConfigValues values = configManager.getConfigValues();
        ConfigValues.QuantityMenuConfig quantityMenu = values.guiConfig().quantityMenu();

        AuctionMenuHolder holder = new AuctionMenuHolder(
                AuctionViewType.QUANTITY, currency, player.getUniqueId(), 0,
                AuctionSort.NEWEST, null, null, auctionItem.id(),
                selectedAmount, 1, null, auctionItem.amount(),
                auctionItem, 0, 0
        );

        Inventory inv = Bukkit.createInventory(holder, quantityMenu.sizeMenu(), TextUtils.component(values.guiConfig().titles().quantity()));
        holder.setInventory(inv);

        fillGlass(inv, quantityMenu.glassPanes());

        updateQuantityDisplay(inv, holder, auctionItem);

        for (int slot : quantityMenu.decrease10().slots()) {
            inv.setItem(slot, buildButton(quantityMenu.decrease10()));
        }
        for (int slot : quantityMenu.decrease1().slots()) {
            inv.setItem(slot, buildButton(quantityMenu.decrease1()));
        }
        for (int slot : quantityMenu.increase1().slots()) {
            inv.setItem(slot, buildButton(quantityMenu.increase1()));
        }
        for (int slot : quantityMenu.increase10().slots()) {
            inv.setItem(slot, buildButton(quantityMenu.increase10()));
        }

        player.openInventory(inv);
    }

    public void openConfirm(Player player, AuctionCurrency currency, long lotId, int amount) {
        ConfigValues.ConfirmMenuConfig config = configManager.getConfigValues().confirmMenu();
        ConfigValues.TitlesConfig titlesConfig = configManager.getConfigValues().guiConfig().titles();
        Map<String, String> messages = configManager.getConfigValues().messages();
        String msg = messages.get("no-id");

        repository.findById(lotId).thenAccept(optItem -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (optItem.isEmpty()) {
                    player.sendMessage(msg);
                    return;
                }
                AuctionItem item = optItem.get();
                int finalAmount = (amount == Integer.MAX_VALUE) ? item.amount() : amount;


                AuctionMenuHolder holder = new AuctionMenuHolder(
                        AuctionViewType.CONFIRM, currency, player.getUniqueId(), 0,
                        AuctionSort.NEWEST, null, null, lotId,
                        1, 1, null, item.amount(),
                        item, finalAmount, lotId
                );

                Inventory inv = Bukkit.createInventory(holder, config.size(), TextUtils.component(titlesConfig.confirmBuy()));
                holder.setInventory(inv);

                fillGlass(inv, config.glassPanes());

                ItemStack centerItem = buildBuyItem(item, finalAmount);
                inv.setItem(config.itemSlot(), centerItem);

                for (int slot : config.confirm().slots()) {
                    if (slot >= 0 && slot < inv.getSize()) {
                        inv.setItem(slot, buildButton(config.confirm()));
                    }
                }
                for (int slot : config.cancel().slots()) {
                    if (slot >= 0 && slot < inv.getSize()) {
                        inv.setItem(slot, buildButton(config.cancel()));
                    }
                }

                player.openInventory(inv);
            });
        });
    }

    public void updateQuantityDisplay(Inventory inventory, AuctionMenuHolder holder, AuctionItem auctionItem) {
        ConfigValues.QuantityMenuConfig qm = configManager.getConfigValues().guiConfig().quantityMenu();
        int amount = holder.selectedAmount();
        ItemStack item = buildBuyItem(auctionItem, amount);
        inventory.setItem(qm.slotAmount(), item);
    }

    public void openInventory(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        repository.findAll(currency).thenAccept(items -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    Inventory inventory = buildMenu(player, viewType, currency, page, sort, sellerFilter, searchFilter, category, items);
                    player.openInventory(inventory);
                } catch (Exception exception) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Ошибка создания меню аукциона", exception);
                    player.sendMessage("§cОшибка открытия аукциона.");
                }
            });
        });
    }

    private void setAction(ItemMeta meta, ActionType action) {
        if (meta == null || action == null) return;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(actionKey, PersistentDataType.STRING, action.name());
    }

    private void addMainButtons(Inventory inventory, ConfigValues values, ConfigValues.GuiConfig gui, AuctionCurrency currency, AuctionSort sort, int totalItems, String category, List<AuctionItem> allItems, Player player) {
        addNavigation(inventory, values, gui, currency, sort, totalItems);

        ConfigValues.NavigationConfig navigation = gui.navigation();

        int sellingCount = 0;
        for (AuctionItem item : allItems) {
            if (!item.sellerName().equalsIgnoreCase(player.getName())) continue;
            if (!item.expired(values.maxAuctionStorageDays())) sellingCount++;
        }

        inventory.setItem(navigation.selling().slot(), buildNavigationButton(navigation.selling(), replaceCount(navigation.selling().lore(), sellingCount)));
        inventory.setItem(navigation.expired().slot(), buildNavigationButton(navigation.expired(), replaceCount(navigation.expired().lore(), 0)));
        inventory.setItem(navigation.sort().slot(), sortItem(sort));
        inventory.setItem(navigation.categories().slot(), categoryItem(category));
    }

    private ItemStack buildBuyItem(AuctionItem auctionItem, int selectedAmount) {
        ConfigValues.ItemLoreConfig loreConfig = configManager.getConfigValues().guiConfig().itemLore();
        List<String> template = loreConfig.buyLore();
        ItemStack item = auctionItem.itemStackClone();
        item.setAmount(Math.min(selectedAmount, item.getMaxStackSize()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON
        );

        List<Component> lore = new ArrayList<>();
        double totalPrice = auctionItem.pricePerItem() * selectedAmount;
        String totalPriceStr = PriceFormatter.format(totalPrice);
        String seller = auctionItem.sellerName();
        String amountStr = String.valueOf(selectedAmount);

        for (String line : template) {
            String result = line
                    .replace("{total_price}", totalPriceStr)
                    .replace("{seller}", seller)
                    .replace("{amount}", amountStr);
            lore.add(TextUtils.component(result));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<AuctionItem> filterItems(Player player, AuctionViewType viewType, List<AuctionItem> items, String sellerFilter, String searchFilter, String category, int maxDays) {
        List<AuctionItem> filtered = new ArrayList<>();
        boolean ownerView = viewType == AuctionViewType.SELLING || viewType == AuctionViewType.EXPIRED;
        boolean expiredView = viewType == AuctionViewType.EXPIRED;

        for (AuctionItem item : items) {
            boolean expired = item.expired(maxDays);

            if (ownerView) {
                if (!item.sellerName().equalsIgnoreCase(player.getName())) continue;
                if (expired != expiredView) continue;
            } else {
                if (expired) continue;
                if (sellerFilter != null && !item.sellerName().equalsIgnoreCase(sellerFilter)) continue;
                if (searchFilter != null && !matchesSearch(item, searchFilter)) continue;
                if (category != null && !category.equalsIgnoreCase("all")) {
                    java.util.Set<Material> materials = configManager.getConfigValues().categories().get(category.toLowerCase());
                    if (materials != null && !materials.contains(item.material())) continue;
                }
            }
            filtered.add(item);
        }
        return filtered;
    }

    private void addNavigation(Inventory inventory, ConfigValues values, ConfigValues.GuiConfig gui, AuctionCurrency currency, AuctionSort sort, int totalItems) {
        ConfigValues.NavigationConfig navigation = gui.navigation();
        inventory.setItem(navigation.previous().slot(), buildNavigationButton(navigation.previous(), navigation.previous().lore()));
        inventory.setItem(navigation.refresh().slot(), buildNavigationButton(navigation.refresh(), navigation.refresh().lore()));
        inventory.setItem(navigation.next().slot(), buildNavigationButton(navigation.next(), replaceCount(navigation.next().lore(), totalItems)));
    }

    private List<String> replaceCount(List<String> lore, int count) {
        List<String> result = new ArrayList<>(lore.size());
        String value = String.valueOf(count);
        for (String line : lore) {
            result.add(line.replace("{count}", value));
        }
        return result;
    }

    private ItemStack sortItem(AuctionSort selected) {
        ConfigValues values = configManager.getConfigValues();
        ConfigValues.SortMenuConfig sortMenu = values.guiConfig().sortMenu();
        ConfigValues.NavigationConfig navigation = values.guiConfig().navigation();

        List<String> lore = new ArrayList<>();
        for (AuctionSort sort : AuctionSort.values()) {
            String display = values.sortingNames().getOrDefault(sort.name(), sort.name());
            String prefix = sort == selected ? sortMenu.selectedPrefix() : sortMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(sortMenu.footer()));

        return buildNavigationButton(navigation.sort(), lore);
    }

    private ItemStack categoryItem(String selectedCategory) {
        ConfigValues values = configManager.getConfigValues();
        ConfigValues.CategoryMenuConfig categoryMenu = values.guiConfig().categoryMenu();
        ConfigValues.NavigationConfig navigation = values.guiConfig().navigation();
        List<String> categories = new ArrayList<>(values.categories().keySet());
        if (!categories.contains("all")) categories.add(0, "all");

        String current = (selectedCategory == null || selectedCategory.isEmpty()) ? "all" : selectedCategory.toLowerCase();

        List<String> lore = new ArrayList<>();
        for (String category : categories) {
            String display = getCategoryDisplayName(category);
            String prefix = category.equalsIgnoreCase(current) ? categoryMenu.selectedPrefix() : categoryMenu.unselectedPrefix();
            lore.add(TextUtils.colorize(prefix + display));
        }
        lore.add("");
        lore.add(TextUtils.colorize(categoryMenu.footer()));

        return buildNavigationButton(navigation.categories(), lore);
    }

    private String getCategoryDisplayName(String category) {
        return configManager.getConfigValues().categoryNames().getOrDefault(category.toLowerCase(),
                category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase());
    }

    private ItemStack buildNavigationButton(ConfigValues.NavigationButton button, List<String> lore) {
        ItemStack item;
        if (button.skullTexture() != null && !button.skullTexture().isEmpty()) {
            item = ItemUtils.skull(button.skullTexture(), button.name(), lore);
        } else {
            item = new ItemStack(button.material());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtils.component(button.name()));
                List<Component> components = new ArrayList<>();
                for (String line : lore) components.add(TextUtils.component(line));
                meta.lore(components);
                item.setItemMeta(meta);
            }
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            setAction(meta, button.action());
            item.setItemMeta(meta);
        }
        return item;
    }

    private Inventory buildMenu(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, List<AuctionItem> items) {
        ConfigValues values = configManager.getConfigValues();
        ConfigValues.GuiConfig gui = values.guiConfig();

        List<AuctionItem> filtered = filterItems(player, viewType, items, sellerFilter, searchFilter, category, values.maxAuctionStorageDays());

        if (viewType == AuctionViewType.MAIN) {
            filtered.sort(sort.comparator());
        } else {
            filtered.sort(java.util.Comparator.comparingLong(AuctionItem::createdAt).reversed());
        }

        List<Integer> slots = values.auctionSlots();
        int pageSize = slots.size();
        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        String title = switch (viewType) {
            case MAIN -> gui.titles().main();
            case SELLING -> gui.titles().selling();
            case EXPIRED -> gui.titles().expired();
            default -> gui.titles().main();
        };
        title = title.replace("{page}", (page + 1) + "/" + totalPages);

        AuctionMenuHolder holder = new AuctionMenuHolder(
                viewType, currency, player.getUniqueId(), page, sort,
                sellerFilter, searchFilter, -1, 1, totalPages, category, 0,
                0, 0
        );
        Inventory inventory = Bukkit.createInventory(holder, values.menuSize(), TextUtils.component(title));
        holder.setInventory(inventory);

        fillGlass(inventory, viewType == AuctionViewType.SELLING ? GLASS_SELLING :
                viewType == AuctionViewType.EXPIRED ? GLASS_EXPIRED : currency.configKey());

        int start = page * pageSize;
        for (int i = 0; i < slots.size(); i++) {
            int index = start + i;
            if (index >= filtered.size()) break;
            AuctionItem item = filtered.get(index);
            int slot = slots.get(i);
            inventory.setItem(slot, displayItem(item));
            holder.addLot(slot, item.id(), item.amount());
        }

        if (viewType == AuctionViewType.MAIN) {
            addMainButtons(inventory, values, gui, currency, sort, filtered.size(), category, items, player);
        } else {
            addNavigation(inventory, values, gui, currency, AuctionSort.NEWEST, filtered.size());
        }

        if (viewType == AuctionViewType.SELLING || viewType == AuctionViewType.EXPIRED) {
            inventory.setItem(values.exitSlot(), buildButton(values.exitButton()));
        }

        return inventory;
    }

    private ItemStack displayItem(AuctionItem auctionItem) {
        ConfigValues.ItemLoreConfig loreConfig = configManager.getConfigValues().guiConfig().itemLore();
        ItemStack item = auctionItem.itemStackClone();
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> originalLore = meta.lore();
        if (originalLore == null) {
            originalLore = new ArrayList<>();
        }

        List<Component> lore = new ArrayList<>(originalLore);

        String symbol = auctionItem.currency().symbol(configManager.getConfigValues());
        String price = PriceFormatter.format(auctionItem.price());
        String pricePerItem = PriceFormatter.format(auctionItem.pricePerItem());
        long daysLeft = configManager.getConfigValues().maxAuctionStorageDays()
                - ((System.currentTimeMillis() - auctionItem.createdAt()) / 86_400_000L);
        String amount = String.valueOf(auctionItem.amount());

        List<String> template = auctionItem.amount() == 1 ? loreConfig.loreOne() : loreConfig.lore();
        for (String line : template) {
            String result = line
                    .replace("{price}", price)
                    .replace("{pricePerItem}", pricePerItem)
                    .replace("{seller}", auctionItem.sellerName())
                    .replace("{amount}", amount)
                    .replace("{daysLeft}", String.valueOf(Math.max(daysLeft, 0)))
                    .replace("{symbol_value}", symbol)
                    .replace("{listedDate}", formatDate(auctionItem.createdAt()));
            lore.add(TextUtils.component(result));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildButton(ConfigValues.ButtonConfig config) {
        ItemStack item;
        if (config.skullTexture() != null && !config.skullTexture().isEmpty()) {
            item = ItemUtils.skull(config.skullTexture(), config.name(), config.lore());
        } else {
            item = ItemUtils.named(config.material(), config.name(), config.lore());
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            setAction(meta, config.action());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildButton(ConfigValues.ConfirmButtonConfig config) {
        ItemStack item;
        if (config.skullTexture() != null && !config.skullTexture().isEmpty()) {
            item = ItemUtils.skull(config.skullTexture(), config.name(), config.lore());
        } else {
            item = ItemUtils.named(config.material(), config.name(), config.lore());
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            setAction(meta, config.action());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillGlass(Inventory inventory, String key) {
        ConfigValues values = configManager.getConfigValues();
        Map<Integer, ConfigValues.GlassPane> panes = switch (key) {
            case GLASS_SELLING -> values.sellingGlassPanes();
            case GLASS_EXPIRED -> values.expiredGlassPanes();
            case GLASS_VAULT -> values.vaultGlassPanes();
            case GLASS_PLAYERPOINTS -> values.playerPointsGlassPanes();
            default -> Map.of();
        };
        fillGlass(inventory, panes);
    }

    private void fillGlass(Inventory inventory, Map<Integer, ConfigValues.GlassPane> panes) {
        for (Map.Entry<Integer, ConfigValues.GlassPane> entry : panes.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= inventory.getSize()) continue;
            ConfigValues.GlassPane pane = entry.getValue();
            ItemStack item = new ItemStack(pane.material());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtils.component(pane.displayName()));
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace(" ", "");
    }

    private boolean matchesSearch(AuctionItem item, String query) {
        return normalize(item.material().name()).contains(normalize(query));
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