package ru.moscow.foxkiss.gui.builder;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionLotType;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.holders.InventoryMenuHolder;
import ru.moscow.foxkiss.gui.holders.MainMenuHolder;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.*;

public final class MenuBuilder {
    private final IConfigManager configManager;
    private final ItemDisplayFactory itemFactory;
    private final Object2ObjectOpenHashMap<ConfigValues.GlassPane, ItemStack> glassPaneCache = new Object2ObjectOpenHashMap<>();

    public MenuBuilder(IConfigManager configManager, ItemDisplayFactory itemFactory, AuctionRepository repository) {
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        buildGlassCache();
    }

    public Inventory buildMainMenu(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, List<AuctionItem> filtered, int totalCount, int pageSize, int sellingCount, int expiredCount) {
        ConfigValues values = configManager.getConfigValues();
        ConfigValues.AuctionGuiConfig gui = values.gui(currency);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
        String pageDisplay = (page + 1) + "/" + totalPages;
        String title = buildTitle(viewType, pageDisplay, gui.titles());

        MainMenuHolder holder = new MainMenuHolder(viewType, currency, player.getUniqueId(), page, sort, sellerFilter, searchFilter, category);
        holder.setTotalPages(totalPages);

        Inventory inv = Bukkit.createInventory(holder, gui.menuSize(), TextUtils.component(title));
        holder.setInventory(inv);

        fillGlass(inv, getGlassPanes(viewType, values, currency));
        fillAuctionItems(inv, filtered, gui.auctionSlots(), holder);
        addNavigationButtons(inv, viewType, sort, category, sellingCount, expiredCount, pageDisplay, holder, currency);
        addExitButton(inv, viewType, pageDisplay, gui, holder);
        return inv;
    }

    public void refreshLotDisplays(Inventory inventory, AuctionMenuHolder holder, List<AuctionItem> filtered, int totalCount, int pageSize) {
        ConfigValues values = configManager.getConfigValues();
        List<Integer> activeSlots = values.gui(holder.getCurrency()).auctionSlots();
        for (int slot : activeSlots) {
            inventory.setItem(slot, null);
        }
        holder.clearLots();

        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
        holder.setTotalPages(totalPages);

        int count = Math.min(filtered.size(), activeSlots.size());
        for (int i = 0; i < count; i++) {
            int slot = activeSlots.get(i);
            AuctionItem item = filtered.get(i);
            boolean isInventory = item.getType() == AuctionLotType.INVENTORY;
            inventory.setItem(slot, isInventory ? itemFactory.createInventoryDisplay(item) : itemFactory.createLotDisplay(item));
            holder.addLot(slot, item.getId(), item.amount(), isInventory);
        }
    }

    public void refreshNavigationButtons(Inventory inventory, AuctionMenuHolder holder) {
        if (holder.getViewType() != AuctionViewType.MAIN) {
            return;
        }

        ConfigValues values = configManager.getConfigValues();
        ConfigValues.AuctionGuiConfig gui = values.gui(holder.getCurrency());
        ConfigValues.NavigationConfig nav = gui.navigation();

        ItemStack sortButton = itemFactory.createSortButton(holder.getSort(), holder.getCurrency());
        inventory.setItem(nav.sort().slot(), sortButton);
        holder.addActions(nav.sort().slot(), nav.sort().actions());

        ItemStack categoryButton = itemFactory.createCategoryButton(holder.getCategory(), holder.getCurrency());
        inventory.setItem(nav.categories().slot(), categoryButton);
        holder.addActions(nav.categories().slot(), nav.categories().actions());
    }

    public void fillGlass(Inventory inventory, Map<Integer, ConfigValues.GlassPane> panes) {
        for (Map.Entry<Integer, ConfigValues.GlassPane> entry : panes.entrySet()) {
            ItemStack cached = glassPaneCache.get(entry.getValue());
            if (cached != null) {
                inventory.setItem(entry.getKey(), cached.clone());
            }
        }
    }

    private String buildTitle(AuctionViewType viewType, String pageDisplay, ConfigValues.TitlesConfig titles) {
        String raw = switch (viewType) {
            case MAIN -> titles.main();
            case SELLING -> titles.selling();
            case EXPIRED -> titles.expired();
            default -> titles.main();
        };
        return raw.replace("{page}", pageDisplay);
    }

    private Map<Integer, ConfigValues.GlassPane> getGlassPanes(AuctionViewType viewType, ConfigValues values, AuctionCurrency currency) {
        return switch (viewType) {
            case MAIN, SELLING -> values.gui(currency).mainGlass();
            case EXPIRED -> values.gui(currency).expiredGlass();
            default -> Collections.emptyMap();
        };
    }

    private void fillAuctionItems(Inventory inv, List<AuctionItem> items, List<Integer> slots, AuctionMenuHolder holder) {
        int count = Math.min(items.size(), slots.size());
        for (int i = 0; i < count; i++) {
            int slot = slots.get(i);
            AuctionItem item = items.get(i);
            boolean inventory = item.getType() == AuctionLotType.INVENTORY;
            inv.setItem(slot, inventory ? itemFactory.createInventoryDisplay(item) : itemFactory.createLotDisplay(item));
            holder.addLot(slot, item.getId(), item.amount(), inventory);
        }
    }

    private void addExitButton(Inventory inv, AuctionViewType viewType, String pageDisplay, ConfigValues.AuctionGuiConfig gui, AuctionMenuHolder holder) {
        ConfigValues.ButtonConfig exit = gui.exitButton();
        if (viewType != AuctionViewType.SELLING && viewType != AuctionViewType.EXPIRED) return;

        ObjectList<String> replacedLore = new ObjectArrayList<>();
        for (String line : exit.lore()) {
            replacedLore.add(line.replace("{page}", pageDisplay));
        }

        ConfigValues.ButtonConfig replaced = new ConfigValues.ButtonConfig(
                exit.material(),
                exit.name().replace("{page}", pageDisplay),
                replacedLore,
                exit.skullTexture(),
                exit.actions(),
                exit.slots(),
                exit.customModelData()
        );

        ItemStack button = itemFactory.createButton(replaced);
        for (int slot : replaced.slots()) {
            inv.setItem(slot, button.clone());
            holder.addActions(slot, replaced.actions());
        }
    }

    private void addNavigationButtons(Inventory inv, AuctionViewType viewType, AuctionSort sort, String category, int sellingCount, int expiredCount, String pageDisplay, AuctionMenuHolder holder, AuctionCurrency currency) {
        ConfigValues.AuctionGuiConfig gui = configManager.getConfigValues().gui(currency);
        ConfigValues.NavigationConfig nav = gui.navigation();

        setNavButton(inv, nav.previous(), pageDisplay, holder);
        setNavButton(inv, nav.refresh(), pageDisplay, holder);
        setNavButton(inv, nav.next(), pageDisplay, holder);

        if (viewType == AuctionViewType.MAIN) {
            setNavButtonWithCount(inv, nav.selling(), pageDisplay, sellingCount, holder);
            setNavButtonWithCount(inv, nav.expired(), pageDisplay, expiredCount, holder);
            inv.setItem(nav.sort().slot(), itemFactory.createSortButton(sort, currency));
            holder.addActions(nav.sort().slot(), nav.sort().actions());
            inv.setItem(nav.categories().slot(), itemFactory.createCategoryButton(category, currency));
            holder.addActions(nav.categories().slot(), nav.categories().actions());
        }
    }

    private void setNavButton(Inventory inv, ConfigValues.NavigationButton button, String pageDisplay, AuctionMenuHolder holder) {
        ObjectList<String> lore = new ObjectArrayList<>();
        for (String line : button.lore()) {
            lore.add(line.replace("{page}", pageDisplay));
        }
        ItemStack item = itemFactory.createNavigationButton(button, lore);
        inv.setItem(button.slot(), item);
        holder.addActions(button.slot(), button.actions());
    }

    private void setNavButtonWithCount(Inventory inv, ConfigValues.NavigationButton button, String pageDisplay, int count, AuctionMenuHolder holder) {
        ObjectList<String> lore = new ObjectArrayList<>();
        for (String line : button.lore()) {
            lore.add(line.replace("{page}", pageDisplay).replace("{count}", String.valueOf(count)));
        }
        ItemStack item = itemFactory.createNavigationButton(button, lore);
        inv.setItem(button.slot(), item);
        holder.addActions(button.slot(), button.actions());
    }

    public void buildInventoryView(InventoryMenuHolder holder, AuctionCurrency currency, AuctionItem lot, int current, int total) {
        ConfigValues values = configManager.getConfigValues();
        ConfigValues.InventorySellingConfig inv = values.inventorySelling();
        ConfigValues.InventoryViewConfig view = inv.view();

        Inventory invUi = holder.getInventory();
        String title = (view.title() == null ? "Инвентарь игрока" : view.title())
                .replace("{current}", String.valueOf(current + 1))
                .replace("{max}", String.valueOf(Math.max(1, total)));

        String price = lot.getCurrency().symbol(values) + PriceFormatter.format(lot.getPrice());
        String seller = lot.getSellerName();

        fillGlass(invUi, view.glassPanes());

        List<ItemStack> contents = lot.getInventoryContents();
        int startSlot = view.startSlot();
        int endSlot = view.endSlot();
        if (contents != null) {
            int slot = startSlot;
            for (ItemStack item : contents) {
                if (slot > endSlot) break;
                if (item != null && item.getType() != Material.AIR) {
                    invUi.setItem(slot, itemFactory.createInventoryItemDisplay(item));
                    slot++;
                }
            }
        }

        if (view.prev() != null && view.prev().slot() >= 0) {
            ConfigValues.InventoryNavButton prev = view.prev();
            List<String> lore = colorize(prev.lore(), current, total);
            invUi.setItem(prev.slot(), itemFactory.createConfigButton(prev.material(), colorize(prev.name()), lore, prev.actions()));
            holder.addActions(prev.slot(), prev.actions());
        }

        if (view.next() != null && view.next().slot() >= 0) {
            ConfigValues.InventoryNavButton next = view.next();
            List<String> lore = colorize(next.lore(), current, total);
            invUi.setItem(next.slot(), itemFactory.createConfigButton(next.material(), colorize(next.name()), lore, next.actions()));
            holder.addActions(next.slot(), next.actions());
        }

        if (view.cancel() != null) {
            ConfigValues.InventoryActionButton cancel = view.cancel();
            for (int slot : cancel.slots()) {
                invUi.setItem(slot, itemFactory.createConfigButton(cancel.material(), colorize(cancel.name()), colorize(cancel.lore(), price, seller), cancel.actions()));
                holder.addActions(slot, cancel.actions());
            }
        }

        if (view.confirm() != null) {
            ConfigValues.InventoryActionButton confirm = view.confirm();
            for (int slot : confirm.slots()) {
                invUi.setItem(slot, itemFactory.createConfigButton(confirm.material(), colorize(confirm.name()), colorize(confirm.lore(), price, seller), confirm.actions()));
                holder.addActions(slot, confirm.actions());
            }
        }

        if (view.itemSlot() >= 0) {
            invUi.setItem(view.itemSlot(), itemFactory.createBuyItem(lot, lot.amount()));
        }
    }

    private List<String> colorize(List<String> lore, int current, int total) {
        ObjectList<String> result = new ObjectArrayList<>();
        for (String line : lore) {
            result.add(TextUtils.colorize(line
                    .replace("{current}", String.valueOf(current + 1))
                    .replace("{max}", String.valueOf(Math.max(1, total)))));
        }
        return result;
    }

    private List<String> colorize(List<String> lore, String price, String seller) {
        ObjectList<String> result = new ObjectArrayList<>();
        for (String line : lore) {
            result.add(TextUtils.colorize(line
                    .replace("%price%", price)
                    .replace("%player%", seller)));
        }
        return result;
    }

    private String colorize(String text) {
        return TextUtils.colorize(text);
    }

    public void buildGlassCache() {
        glassPaneCache.clear();
        ConfigValues config = configManager.getConfigValues();
        cachePanes(config.moneyGui().mainGlass());
        cachePanes(config.moneyGui().expiredGlass());
        cachePanes(config.donateGui().mainGlass());
        cachePanes(config.donateGui().expiredGlass());
        cachePanes(config.confirmMenu().glassPanes());
        cachePanes(config.quantityMenu().glassPanes());
    }

    private void cachePanes(Map<Integer, ConfigValues.GlassPane> panes) {
        for (ConfigValues.GlassPane pane : panes.values()) {
            glassPaneCache.putIfAbsent(pane, createGlassPane(pane));
        }
    }

    private ItemStack createGlassPane(ConfigValues.GlassPane pane) {
        ItemStack item = new ItemStack(pane.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtils.component(pane.displayName()));
            if (pane.customModelData() != null) {
                meta.setCustomModelData(pane.customModelData());
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
