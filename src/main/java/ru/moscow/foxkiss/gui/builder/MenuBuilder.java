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
import ru.moscow.foxkiss.gui.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.*;

public final class MenuBuilder {

    private final IConfigManager configManager;
    private final ItemDisplayFactory itemFactory;
    private final Map<ConfigValues.GlassPane, ItemStack> glassPaneCache = new HashMap<>();

    public MenuBuilder(IConfigManager configManager, ItemDisplayFactory itemFactory) {
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        buildpaneGlass();
    }

    public Inventory buildMainMenu(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, List<AuctionItem> filtered, int totalCount, int pageSize, int sellingCount, int expiredCount) {
        ConfigValues values = configManager.getConfigValues();

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages < 1) totalPages = 1;

        String pageDisplay = (page + 1) + "/" + totalPages;
        
        String title = buildTitle(viewType, pageDisplay, values.guiConfig().titles());

        AuctionMenuHolder holder = createHolder(player, viewType, currency, page, sort, sellerFilter, searchFilter, category, totalPages);
        Inventory inv = Bukkit.createInventory(holder, values.menuSize(), TextUtils.component(title));
        holder.setInventory(inv);

        fillGlassPanes(inv, viewType, values);
        fillAuctionItems(inv, filtered, values.auctionSlots(), holder);
        addNavigationButtons(inv, viewType, page, totalPages, sort, category, sellingCount, expiredCount, pageDisplay);
        addExitButton(inv, viewType, pageDisplay, values);

        return inv;
    }

    public void refreshLotDisplays(Inventory inventory, AuctionMenuHolder holder, List<AuctionItem> filtered, int totalCount, int pageSize) {
        List<Integer> activeSlots = new ArrayList<>(configManager.getConfigValues().auctionSlots());
        clearSlots(inventory, activeSlots);
        holder.clearLots();
        
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages < 1) totalPages = 1;
        holder.setTotalPages(totalPages);

        int count = Math.min(filtered.size(), activeSlots.size());

        for (int i = 0; i < count; i++) {
            int slot = activeSlots.get(i);
            AuctionItem item = filtered.get(i);
            inventory.setItem(slot, itemFactory.createLotDisplay(item));
            holder.addLot(slot, item.id(), item.amount());
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

    private AuctionMenuHolder createHolder(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, int totalPages) {
        return AuctionMenuHolder.builder()
                .viewType(viewType)
                .currency(currency)
                .viewer(player.getUniqueId())
                .page(page)
                .sort(sort)
                .sellerFilter(sellerFilter)
                .searchFilter(searchFilter)
                .category(category)
                .totalPages(totalPages)
                .build();
    }

    private void fillGlassPanes(Inventory inv, AuctionViewType viewType, ConfigValues values) {
        Map<Integer, ConfigValues.GlassPane> panes = switch (viewType) {
            case MAIN, SELLING -> values.sellingGlassPanes();
            case EXPIRED -> values.expiredGlassPanes();
            default -> Collections.emptyMap();
        };
        fillGlass(inv, panes);
    }

    private void fillAuctionItems(Inventory inv, List<AuctionItem> items, Set<Integer> slotsSet, AuctionMenuHolder holder) {
        List<Integer> activeSlots = new ArrayList<>(slotsSet);

        int count = Math.min(items.size(), activeSlots.size());

        for (int i = 0; i < count; i++) {
            int slot = activeSlots.get(i);
            AuctionItem item = items.get(i);

            inv.setItem(slot, itemFactory.createLotDisplay(item));
            holder.addLot(slot, item.id(), item.amount());
        }
    }

    private void addExitButton(Inventory inv, AuctionViewType viewType, String pageDisplay, ConfigValues values) {
        ConfigValues.ButtonConfig exit = values.exitButton();
        if (exit == null || (viewType != AuctionViewType.SELLING && viewType != AuctionViewType.EXPIRED)) {
            return;
        }

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
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, button.clone());
            }
        }
    }

    private void addNavigationButtons(Inventory inv, AuctionViewType viewType, int page, int totalPages, AuctionSort sort, String category, int sellingCount, int expiredCount, String pageDisplay) {
        ConfigValues.NavigationConfig nav = configManager.getConfigValues().guiConfig().navigation();

        setNavButton(inv, nav.previous(), pageDisplay, null);
        setNavButton(inv, nav.refresh(), pageDisplay, null);
        setNavButton(inv, nav.next(), pageDisplay, null);

        if (viewType == AuctionViewType.MAIN) {
            setNavButton(inv, nav.selling(), pageDisplay, sellingCount);
            setNavButtonWithCount(inv, nav.expired(), pageDisplay, expiredCount);

            inv.setItem(nav.sort().slot(), itemFactory.createSortButton(sort));
            inv.setItem(nav.categories().slot(), itemFactory.createCategoryButton(category));
        }
    }

    private void setNavButton(Inventory inv, ConfigValues.NavigationButton button, String pageDisplay, Integer count) {
        List<String> lore = replacePlaceholders(button.lore(), pageDisplay, count);
        inv.setItem(button.slot(), itemFactory.createNavigationButton(button, lore));
    }

    private void setNavButtonWithCount(Inventory inv, ConfigValues.NavigationButton button, String pageDisplay, int count) {
        List<String> lore = new ArrayList<>(button.lore().size());
        String countStr = String.valueOf(count);
        for (String line : button.lore()) {
            lore.add(line.replace("{page}", pageDisplay).replace("{count}", countStr));
        }
        inv.setItem(button.slot(), itemFactory.createNavigationButton(button, lore));
    }

    private List<String> replacePlaceholders(List<String> lore, String pageDisplay, Integer count) {
        List<String> result = new ArrayList<>(lore.size());
        for (String line : lore) {
            String replaced = line.replace("{page}", pageDisplay);
            if (count != null) {
                replaced = replaced.replace("{count}", String.valueOf(count));
            }
            result.add(replaced);
        }
        return result;
    }

    public void fillGlass(Inventory inventory, Map<Integer, ConfigValues.GlassPane> panes) {
        for (Map.Entry<Integer, ConfigValues.GlassPane> entry : panes.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= inventory.getSize()) continue;

            ItemStack cached = glassPaneCache.get(entry.getValue());
            if (cached != null) {
                inventory.setItem(slot, cached.clone());
            }
        }
    }

    public void buildpaneGlass() {
        glassPaneCache.clear();
        ConfigValues config = configManager.getConfigValues();

        cacheGlassPanes(config.sellingGlassPanes());
        cacheGlassPanes(config.expiredGlassPanes());
        cacheGlassPanes(config.vaultGlassPanes());
        cacheGlassPanes(config.playerPointsGlassPanes());
        cacheGlassPanes(config.confirmMenu().glassPanes());
        cacheGlassPanes(config.guiConfig().quantityMenu().glassPanes());
    }

    private void cacheGlassPanes(Map<Integer, ConfigValues.GlassPane> panes) {
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

    private void clearSlots(Inventory inv, List<Integer> slots) {
        for (int slot : slots) {
            inv.setItem(slot, null);
        }
    }
}