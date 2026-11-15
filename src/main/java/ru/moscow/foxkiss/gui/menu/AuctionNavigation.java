package ru.moscow.foxkiss.gui.menu;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionMenu;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.holders.SalesHistoryMenuHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AuctionNavigation {
    private final IConfigManager configManager;
    private final AuctionMenu auctionMenu;

    public AuctionNavigation(IConfigManager configManager, AuctionMenu auctionMenu) {
        this.configManager = configManager;
        this.auctionMenu = auctionMenu;
        reloadCategories();
    }

    public void onPlayerQuit(UUID uuid) { selectionTimes.removeLong(uuid); }

    private final Map<AuctionCurrency, List<String>> cats = new EnumMap<>(AuctionCurrency.class);
    private final Object2LongMap<UUID> selectionTimes = new Object2LongOpenHashMap<>();
    private final Map<AuctionCurrency, List<AuctionSort>> sortOrders = new EnumMap<>(AuctionCurrency.class);

    public void reloadCategories() {
        cats.clear();
        sortOrders.clear();
        AuctionSort[] availableSorts = AuctionSort.values();
        for (AuctionCurrency currency : AuctionCurrency.values()) {
            List<AuctionSort> sorts = new ArrayList<>();
            List<String> sortLore = configManager.getConfigValues().gui(currency).navigation().sort().lore();
            if (sortLore.isEmpty()) Collections.addAll(sorts, availableSorts);
            else {
                for (String line : sortLore) {
                    for (AuctionSort sort : availableSorts) {
                        if (!sorts.contains(sort) && line.contains("{sort:" + sort.name() + "}")) {
                            sorts.add(sort);
                        }
                    }
                }
            }
            sortOrders.put(currency, List.copyOf(sorts));
            List<String> ids = categoryIds(currency);
            cats.put(currency, List.copyOf(ids));
        }
    }

    private List<String> categoryIds(AuctionCurrency currency) {
        List<String> ids = new ArrayList<>();
        List<ConfigValues.CategoryEntry> entries = configManager.getConfigValues().gui(currency).categoryMenu().entries();
        if (entries == null) {
            ids.add("all");
            for (String id : configManager.getConfigValues().categories().keySet()) if (!id.equals("all")) ids.add(id);
        } else {
            for (ConfigValues.CategoryEntry entry : entries) if (!entry.id().isEmpty()) ids.add(entry.id());
        }
        return ids;
    }

    public void handle(Player player, AuctionMenuHolder holder, ActionType action, boolean rightClick) {
        int currentPage = holder.getPage();
        int totalPages = holder.getTotalPages();

        switch (action) {
            case MAIN -> auctionMenu.openMain(player, holder.getCurrency(), 0, null, null, null, null);

            case SELLING -> auctionMenu.openSelling(player, holder.getCurrency(), 0);

            case HISTORY -> auctionMenu.openSalesHistory(player, holder.getCurrency(), 0);

            case PROFILE -> auctionMenu.openProfile(player, holder.getCurrency());

            case DISCORD -> auctionMenu.openProfileInput(player, true);

            case STORE -> auctionMenu.openProfileInput(player, false);

            case EXPIRED -> auctionMenu.openExpired(player, holder.getCurrency(), 0);

            case PREVIOUS -> {
                if (currentPage <= 0) return;
                if (holder instanceof SalesHistoryMenuHolder) {
                    auctionMenu.openSalesHistory(player, holder.getCurrency(), currentPage - 1);
                    return;
                }
                holder.setPage(currentPage - 1);
                auctionMenu.refreshInventory(player, holder);
            }
            case NEXT -> {
                if (currentPage + 1 >= totalPages) return;
                if (holder instanceof SalesHistoryMenuHolder) {
                    auctionMenu.openSalesHistory(player, holder.getCurrency(), currentPage + 1);
                    return;
                }
                holder.setPage(currentPage + 1);
                auctionMenu.refreshInventory(player, holder);
            }
            case REFRESH -> auctionMenu.refreshInventory(player, holder);

            case SORT -> {
                if (auctionMenu.isRefreshing(player) || trySwitchSelection(player.getUniqueId())) return;
                List<AuctionSort> order = sortOrders.get(holder.getCurrency());
                if (order.isEmpty()) return;
                int index = order.indexOf(holder.getSort());
                int next = index < 0 ? (rightClick ? order.size() - 1 : 0)
                        : Math.floorMod(index + (rightClick ? -1 : 1), order.size());
                AuctionSort newSort = order.get(next);
                holder.setSort(newSort);
                holder.setPage(0);
                auctionMenu.refreshInventory(player, holder);
            }
            case CATEGORIES -> {
                if (auctionMenu.isRefreshing(player) || trySwitchSelection(player.getUniqueId())) return;
                if (cats.get(holder.getCurrency()).isEmpty()) return;
                String newCat = switchCategory(holder.getCategory(), holder.getCurrency(), rightClick ? -1 : 1);
                holder.setCategory(newCat);
                holder.setPage(0);
                auctionMenu.refreshInventory(player, holder);
            }
            default -> {}
        }
    }

    private boolean trySwitchSelection(UUID uuid) {
        long now = System.nanoTime();
        long previous = selectionTimes.getLong(uuid);

        if (previous != 0L && now - previous < 10_000_000L) {
            return true;
        }
        selectionTimes.put(uuid, now);
        return false;
    }

    private String switchCategory(String current, AuctionCurrency currency, int direction) {
        List<String> categories = cats.get(currency);

        if (categories == null || categories.isEmpty()) {
            return "all";
        }

        int index = current == null ? -1 : categories.indexOf(current.toLowerCase(Locale.ROOT));

        if (index < 0) {
            return direction > 0 ? categories.getFirst() : categories.getLast();
        }

        return categories.get(Math.floorMod(index + direction, categories.size()));
    }

}
