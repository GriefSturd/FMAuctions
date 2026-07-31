package ru.moscow.foxkiss.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.builder.MenuBuilder;
import ru.moscow.foxkiss.gui.services.AuctionFilterService;
import ru.moscow.foxkiss.utils.CacheManager;

import java.util.*;

public final class AuctionMenu {

    private final FMAuction plugin;
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final PlayerPreferences playerPreferences;
    private final MenuBuilder builder;
    private final AuctionFilterService filterService;
    private final QuantityMenuController quantityController;
    private final ConfirmMenuController confirmController;
    private final ItemDisplayFactory itemFactory;

    private final Set<UUID> refreshesInProgress = Collections.newSetFromMap(new HashMap<>());

    public AuctionMenu(FMAuction plugin, IConfigManager configManager, AuctionRepository repository, PlayerPreferences playerPreferences, CacheManager cacheManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.playerPreferences = playerPreferences;

        this.itemFactory = new ItemDisplayFactory(plugin, configManager, cacheManager);
        this.filterService = new AuctionFilterService(configManager);
        this.builder = new MenuBuilder(configManager, itemFactory);
        this.quantityController = new QuantityMenuController(configManager, itemFactory, builder);
        this.confirmController = new ConfirmMenuController(configManager, itemFactory, builder);

        cacheManager.registerClearTask(() -> refreshesInProgress.clear());
    }

    public void openMain(Player player, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        if (sort == null) sort = playerPreferences.getSort(player.getUniqueId(), currency);
        if (category == null || category.isEmpty())
            category = playerPreferences.getCategory(player.getUniqueId(), currency);
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
        repository.findById(lotId).thenAccept(optItem -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (optItem.isEmpty()) {
                    player.sendMessage(configManager.getConfigValues().messages().get("no-id"));
                    return;
                }
                quantityController.openQuantity(player, currency, optItem.get(), 1);
            });
        });
    }

    public void openConfirm(Player player, AuctionCurrency currency, long lotId, int amount) {
        repository.findById(lotId).thenAccept(optItem -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (optItem.isEmpty()) {
                    player.sendMessage(configManager.getConfigValues().messages().get("no-id"));
                    return;
                }
                confirmController.openConfirm(player, currency, optItem.get(), amount);
            });
        });
    }

    public void updateQuantityDisplay(Inventory inventory, AuctionMenuHolder holder, AuctionItem item) {
        quantityController.updateQuantityDisplay(inventory, holder, item);
    }

    public void clearCaches() {
        itemFactory.clearLotDisplayCache();
        builder.buildpaneGlass();
    }

    public void openInventory(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        loadAndRender(player, viewType, currency, page, sort, sellerFilter, searchFilter, category, null, null);
    }

    public void refreshInventory(Player player, AuctionMenuHolder holder) {
        UUID playerId = player.getUniqueId();
        if (!refreshesInProgress.add(playerId)) return;
        loadAndRender(player, holder.viewType(), holder.currency(), holder.page(), holder.sort(),
                holder.sellerFilter(), holder.searchFilter(), holder.category(), holder,
                () -> refreshesInProgress.remove(playerId));
    }

    public void removeRefreshProgress(UUID playerId) {
        refreshesInProgress.remove(playerId);
    }

    private void loadAndRender(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, AuctionMenuHolder targetHolder, Runnable onFinished) {
        repository.findAll(currency).thenAccept(items -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (targetHolder != null && player.getOpenInventory().getTopInventory().getHolder() != targetHolder) {
                        return;
                    }
                    List<AuctionItem> filtered = filterService.filter(player, viewType, items,
                            sellerFilter, searchFilter, category);
                    if (viewType == AuctionViewType.MAIN) {
                        filtered.sort(sort.comparator());
                    } else {
                        filtered.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
                    }

                    int maxStorageDays = configManager.getConfigValues().maxAuctionStorageDays();
                    int expiredCount = 0;
                    int sellingCount = 0;
                    String playerName = player.getName();

                    for (AuctionItem item : items) {
                        if (!item.sellerName().equalsIgnoreCase(playerName)) {
                            continue;
                        }
                        if (item.expired(maxStorageDays)) {
                            expiredCount++;
                        } else {
                            sellingCount++;
                        }
                    }

                    if (targetHolder != null) {
                        builder.refreshLotDisplays(targetHolder.getInventory(), targetHolder, filtered);
                        return;
                    }

                    Inventory inventory = builder.buildMainMenu(player, viewType, currency, page, sort, sellerFilter, searchFilter, category, filtered, sellingCount, expiredCount);
                    if (targetHolder == null) {
                        player.openInventory(inventory);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка открытия меню: " + e.getMessage());
                    player.sendMessage("§cОшибка открытия аукциона.");
                } finally {
                    if (onFinished != null) onFinished.run();
                }
            });
        }).exceptionally(error -> {
            if (onFinished != null) onFinished.run();
            plugin.getLogger().warning("Ошибка загрузки аукциона в инвентарь: " + error.getMessage());
            return null;
        });
    }
}