package ru.moscow.foxkiss.gui.builder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.holders.MainMenuHolder;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.*;

public final class MenuBuilder {
    private final IConfigManager configManager;
    private final ItemDisplayFactory itemFactory;
    private final Map<ConfigValues.GlassPane, ItemStack> glassPaneCache = new HashMap<>();

    public MenuBuilder(IConfigManager configManager, ItemDisplayFactory itemFactory) {
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        buildGlassCache();
    }

    public Inventory buildMainMenu(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, List<AuctionItem> filtered, int totalCount, int pageSize, int sellingCount, int expiredCount) {
        ConfigValues values = configManager.getConfigValues();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
        String pageDisplay = (page + 1) + "/" + totalPages;
        String title = buildTitle(viewType, pageDisplay, values.guiConfig().titles());

        MainMenuHolder holder = new MainMenuHolder(viewType, currency, player.getUniqueId(), page, sort, sellerFilter, searchFilter, category);
        holder.setTotalPages(totalPages);

        Inventory inv = Bukkit.createInventory(holder, values.menuSize(), TextUtils.component(title));
        holder.setInventory(inv);

        fillGlass(inv, getGlassPanes(viewType, values));
        fillAuctionItems(inv, filtered, values.auctionSlots(), holder);
        addNavigationButtons(inv, viewType, sort, category, sellingCount, expiredCount, pageDisplay, holder);
        addExitButton(inv, viewType, pageDisplay, values, holder);
        return inv;
    }

    public void refreshLotDisplays(Inventory inventory, AuctionMenuHolder holder, List<AuctionItem> filtered, int totalCount, int pageSize) {
        List<Integer> activeSlots = configManager.getConfigValues().auctionSlots();
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
            inventory.setItem(slot, itemFactory.createLotDisplay(item));
            holder.addLot(slot, item.getId(), item.amount());
        }
    }

    public void refreshNavigationButtons(Inventory inventory, AuctionMenuHolder holder) {
        if (holder.getViewType() != AuctionViewType.MAIN) {
            return;
        }

        ConfigValues config = configManager.getConfigValues();
        ConfigValues.NavigationConfig nav = config.guiConfig().navigation();

        ItemStack sortButton = itemFactory.createSortButton(holder.getSort());
        inventory.setItem(nav.sort().slot(), sortButton);
        holder.addAction(nav.sort().slot(), nav.sort().action());

        ItemStack categoryButton = itemFactory.createCategoryButton(holder.getCategory());
        inventory.setItem(nav.categories().slot(), categoryButton);
        holder.addAction(nav.categories().slot(), nav.categories().action());
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

    private Map<Integer, ConfigValues.GlassPane> getGlassPanes(AuctionViewType viewType, ConfigValues values) {
        return switch (viewType) {
            case MAIN, SELLING -> values.sellingGlassPanes();
            case EXPIRED -> values.expiredGlassPanes();
            default -> Collections.emptyMap();
        };
    }

    private void fillAuctionItems(Inventory inv, List<AuctionItem> items, List<Integer> slots, AuctionMenuHolder holder) {
        int count = Math.min(items.size(), slots.size());
        for (int i = 0; i < count; i++) {
            int slot = slots.get(i);
            AuctionItem item = items.get(i);
            inv.setItem(slot, itemFactory.createLotDisplay(item));
            holder.addLot(slot, item.getId(), item.amount());
        }
    }

    private void addExitButton(Inventory inv, AuctionViewType viewType, String pageDisplay, ConfigValues values, AuctionMenuHolder holder) {
        ConfigValues.ButtonConfig exit = values.exitButton();
        if (exit == null || (viewType != AuctionViewType.SELLING && viewType != AuctionViewType.EXPIRED)) return;

        List<String> replacedLore = new ArrayList<>();
        for (String line : exit.lore()) {
            replacedLore.add(line.replace("{page}", pageDisplay));
        }

        ConfigValues.ButtonConfig replaced = new ConfigValues.ButtonConfig(
                exit.material(),
                exit.name().replace("{page}", pageDisplay),
                replacedLore,
                exit.skullTexture(),
                exit.action(),
                exit.slots(),
                exit.customModelData()
        );

        ItemStack button = itemFactory.createButton(replaced);
        for (int slot : replaced.slots()) {
            inv.setItem(slot, button.clone());
            holder.addAction(slot, replaced.action());
        }
    }

    private void addNavigationButtons(Inventory inv, AuctionViewType viewType, AuctionSort sort, String category, int sellingCount, int expiredCount, String pageDisplay, AuctionMenuHolder holder) {
        ConfigValues.NavigationConfig nav = configManager.getConfigValues().guiConfig().navigation();

        setNavButton(inv, nav.previous(), pageDisplay, holder);
        setNavButton(inv, nav.refresh(), pageDisplay, holder);
        setNavButton(inv, nav.next(), pageDisplay, holder);

        if (viewType == AuctionViewType.MAIN) {
            setNavButtonWithCount(inv, nav.selling(), pageDisplay, sellingCount, holder);
            setNavButtonWithCount(inv, nav.expired(), pageDisplay, expiredCount, holder);
            inv.setItem(nav.sort().slot(), itemFactory.createSortButton(sort));
            holder.addAction(nav.sort().slot(), nav.sort().action());
            inv.setItem(nav.categories().slot(), itemFactory.createCategoryButton(category));
            holder.addAction(nav.categories().slot(), nav.categories().action());
        }
    }

    private void setNavButton(Inventory inv, ConfigValues.NavigationButton button, String pageDisplay, AuctionMenuHolder holder) {
        List<String> lore = new ArrayList<>();
        for (String line : button.lore()) {
            lore.add(line.replace("{page}", pageDisplay));
        }
        ItemStack item = itemFactory.createNavigationButton(button, lore);
        inv.setItem(button.slot(), item);
        holder.addAction(button.slot(), button.action());
    }

    private void setNavButtonWithCount(Inventory inv, ConfigValues.NavigationButton button, String pageDisplay,
                                       int count, AuctionMenuHolder holder) {
        List<String> lore = new ArrayList<>();
        for (String line : button.lore()) {
            lore.add(line.replace("{page}", pageDisplay).replace("{count}", String.valueOf(count)));
        }
        ItemStack item = itemFactory.createNavigationButton(button, lore);
        inv.setItem(button.slot(), item);
        holder.addAction(button.slot(), button.action());
    }

    public void buildGlassCache() {
        glassPaneCache.clear();
        ConfigValues config = configManager.getConfigValues();
        cachePanes(config.sellingGlassPanes());
        cachePanes(config.expiredGlassPanes());
        cachePanes(config.vaultGlassPanes());
        cachePanes(config.playerPointsGlassPanes());
        cachePanes(config.confirmMenu().glassPanes());
        cachePanes(config.guiConfig().quantityMenu().glassPanes());
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