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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MenuBuilder {

    private final IConfigManager configManager;
    private final ItemDisplayFactory itemFactory;
    private final Map<ConfigValues.GlassPane, ItemStack> glassPaneCache = new HashMap<>();

    public MenuBuilder(IConfigManager configManager, ItemDisplayFactory itemFactory) {
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        buildpaneGlass();
    }

    public Inventory buildMainMenu(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, List<AuctionItem> filtered, int sellingCount, int expiredCount) {
        ConfigValues values = configManager.getConfigValues();
        int menuSize = values.menuSize();
        ConfigValues.TitlesConfig titles = values.guiConfig().titles();

        int totalPages = (int) Math.ceil((double) filtered.size() / values.auctionSlots().size());
        String pageDisplay = (totalPages > 0) ? (page + 1) + "/" + totalPages : "1/1";

        String title;
        switch (viewType) {
            case MAIN -> title = titles.main();
            case SELLING -> title = titles.selling();
            case EXPIRED -> title = titles.expired();
            default -> title = titles.main();
        }
        title = title.replace("{page}", pageDisplay);

        AuctionMenuHolder holder = AuctionMenuHolder.builder()
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

        Inventory inv = Bukkit.createInventory(holder, menuSize, TextUtils.component(title));
        holder.setInventory(inv);

        Map<Integer, ConfigValues.GlassPane> glassPanes;
        switch (viewType) {
            case MAIN -> glassPanes = values.sellingGlassPanes();
            case SELLING -> glassPanes = values.sellingGlassPanes();
            case EXPIRED -> glassPanes = values.expiredGlassPanes();
            default -> glassPanes = Map.of();
        }
        fillGlass(inv, glassPanes);

        List<Integer> activeSlots = new ArrayList<>(values.auctionSlots());
        int startIndex = page * activeSlots.size();
        int endIndex = Math.min(startIndex + activeSlots.size(), filtered.size());
        List<AuctionItem> pageItems = filtered.subList(startIndex, endIndex);

        for (int i = 0; i < pageItems.size(); i++) {
            int slot = activeSlots.get(i);
            AuctionItem item = pageItems.get(i);
            ItemStack display = itemFactory.createLotDisplay(item);
            inv.setItem(slot, display);
            holder.addLot(slot, item.id(), item.amount());
        }

        addNavigationButtons(inv, viewType, page, totalPages, sort, category, sellingCount, expiredCount);

        ConfigValues.ButtonConfig exitButton = values.exitButton();
        if (exitButton != null && (viewType == AuctionViewType.SELLING || viewType == AuctionViewType.EXPIRED)) {
            List<String> replacedLore = new ArrayList<>(exitButton.lore().size());
            for (String line : exitButton.lore()) {
                replacedLore.add(line.replace("{page}", pageDisplay));
            }
            ConfigValues.ButtonConfig replacedExit = new ConfigValues.ButtonConfig(
                    exitButton.material(),
                    exitButton.name().replace("{page}", pageDisplay),
                    replacedLore,
                    exitButton.skullTexture(),
                    exitButton.action(),
                    exitButton.slots()
            );
            for (int slot : replacedExit.slots()) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, itemFactory.createButton(replacedExit));
                }
            }
        }

        return inv;
    }

    public void refreshLotDisplays(Inventory inventory, AuctionMenuHolder holder, List<AuctionItem> filtered) {
        List<Integer> activeSlots = new ArrayList<>(configManager.getConfigValues().auctionSlots());
        for (int slot : activeSlots) {
            inventory.setItem(slot, null);
        }

        holder.clearLots();
        int totalPages = (int) Math.ceil((double) filtered.size() / activeSlots.size());
        holder.totalPages(totalPages);

        int startIndex = holder.page() * activeSlots.size();
        int endIndex = Math.min(startIndex + activeSlots.size(), filtered.size());
        for (int index = startIndex; index < endIndex; index++) {
            int slot = activeSlots.get(index - startIndex);
            AuctionItem item = filtered.get(index);
            inventory.setItem(slot, itemFactory.createLotDisplay(item));
            holder.addLot(slot, item.id(), item.amount());
        }
    }

    private void addNavigationButtons(Inventory inv, AuctionViewType viewType, int page, int totalPages, AuctionSort sort, String category, int sellingCount, int expiredCount) {
        ConfigValues.NavigationConfig nav = configManager.getConfigValues().guiConfig().navigation();
        String pageDisplay = (totalPages > 0) ? (page + 1) + "/" + totalPages : "1/1";

        {
            List<String> lore = replacePlaceholders(nav.previous().lore(), pageDisplay);
            inv.setItem(nav.previous().slot(),
                    itemFactory.createNavigationButton(nav.previous(), lore));
        }

        {
            List<String> lore = replacePlaceholders(nav.refresh().lore(), pageDisplay);
            inv.setItem(nav.refresh().slot(),
                    itemFactory.createNavigationButton(nav.refresh(), lore));
        }

        {
            List<String> lore = replacePlaceholders(nav.next().lore(), pageDisplay);
            inv.setItem(nav.next().slot(),
                    itemFactory.createNavigationButton(nav.next(), lore));
        }

        if (viewType == AuctionViewType.MAIN) {
            {
                List<String> lore = replacePlaceholders(nav.selling().lore(), pageDisplay, sellingCount);
                inv.setItem(nav.selling().slot(),
                        itemFactory.createNavigationButton(nav.selling(), lore));
            }

            {
                List<String> lore = new ArrayList<>(nav.expired().lore().size());
                String countStr = String.valueOf(expiredCount);
                for (String line : nav.expired().lore()) {
                    lore.add(line.replace("{page}", pageDisplay).replace("{count}", countStr));
                }
                inv.setItem(nav.expired().slot(),
                        itemFactory.createNavigationButton(nav.expired(), lore));
            }

            inv.setItem(nav.sort().slot(), itemFactory.createSortButton(sort));
            inv.setItem(nav.categories().slot(), itemFactory.createCategoryButton(category));
        }
    }

    private List<String> replacePlaceholders(List<String> lore, String pageDisplay) {
        return replacePlaceholders(lore, pageDisplay, null);
    }

    private List<String> replacePlaceholders(List<String> lore, String pageDisplay, Integer count) {
        List<String> result = new ArrayList<>(lore.size());
        for (String line : lore) {
            String replaced = line.replace("{page}", pageDisplay);
            result.add(count == null ? replaced : replaced.replace("{count}", String.valueOf(count)));
        }
        return result;
    }

    public void fillGlass(Inventory inventory, Map<Integer, ConfigValues.GlassPane> panes) {
        for (Map.Entry<Integer, ConfigValues.GlassPane> entry : panes.entrySet()) {
            int slot = entry.getKey();

            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            ItemStack item = glassPaneCache.get(entry.getValue());

            if (item != null) {
                inventory.setItem(slot, item.clone());
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
        if (panes.isEmpty()) {
            return;
        }

        for (ConfigValues.GlassPane pane : panes.values()) {
            if (pane == null) {
                continue;
            }

            glassPaneCache.putIfAbsent(pane, createGlassPane(pane));
        }
    }

    private ItemStack createGlassPane(ConfigValues.GlassPane pane) {
        ItemStack item = new ItemStack(pane.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtils.component(pane.displayName()));
            item.setItemMeta(meta);
        }
        return item;
    }
}
