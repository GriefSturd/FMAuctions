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
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.*;

public final class AuctionMenu {
    private final FMAuction plugin;
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final PlayerPreferences playerPreferences;
    private final MenuBuilder builder;
    private final QuantityMenuController quantityController;
    private final ConfirmMenuController confirmController;
    private final ItemDisplayFactory itemFactory;
    private final SchedulerService scheduler;
    private final Set<UUID> refreshesInProgress = new HashSet<>();

    public AuctionMenu(FMAuction plugin, IConfigManager configManager, AuctionRepository repository, PlayerPreferences playerPreferences) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.playerPreferences = playerPreferences;
        this.scheduler = new SchedulerService(plugin);
        this.itemFactory = new ItemDisplayFactory(plugin, configManager);
        this.builder = new MenuBuilder(configManager, itemFactory);
        this.quantityController = new QuantityMenuController(configManager, itemFactory, builder);
        this.confirmController = new ConfirmMenuController(configManager, itemFactory, builder);
    }

    public void openMain(Player player, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        UUID uuid = player.getUniqueId();

        if (sort == null) {
            sort = playerPreferences.getSort(uuid, currency);
        } else {
            playerPreferences.setSort(uuid, currency, sort);
        }

        if (category == null) {
            category = playerPreferences.getCategory(uuid, currency);
        } else {
            playerPreferences.setCategory(uuid, currency, category);
        }

        openInventory(player, AuctionViewType.MAIN, currency, page, sort, sellerFilter, searchFilter, category);
    }

    public void openSelling(Player player, AuctionCurrency currency, int page) {
        openInventory(player, AuctionViewType.SELLING, currency, page, AuctionSort.NEWEST, null, null, null);
    }

    public void openExpired(Player player, AuctionCurrency currency, int page) {
        openInventory(player, AuctionViewType.EXPIRED, currency, page, AuctionSort.NEWEST, null, null, null);
    }

    public void openQuantity(Player player, AuctionCurrency currency, long lotId) {
        scheduler.runAsyncThenSync(
            () -> repository.findById(lotId).orElse(null),
            item -> {
                if (!player.isOnline()) return;
                
                if (item == null) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, 
                            configManager.getConfigValues().messages().noId(), configManager));
                    return;
                }
                
                quantityController.openQuantity(player, currency, item, 1);
            }
        );
    }

    public void openConfirm(Player player, AuctionCurrency currency, long lotId, int amount) {
        scheduler.runAsyncThenSync(
            () -> repository.findById(lotId).orElse(null),
            item -> {
                if (!player.isOnline()) return;
                
                if (item == null) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, 
                            configManager.getConfigValues().messages().noId(), configManager));
                    return;
                }
                
                confirmController.openConfirm(player, currency, item, amount);
            }
        );
    }

    public void updateQuantityDisplay(Inventory inventory, AuctionMenuHolder holder, AuctionItem item) {
        quantityController.updateQuantityDisplay(inventory, holder, item);
    }

    public void clearCaches() {
        itemFactory.clearCache();
        builder.buildpaneGlass();
    }

    public void openInventory(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        loadAndRender(player, viewType, currency, page, sort, sellerFilter, searchFilter, category, null, null);
    }

    public void refreshInventory(Player player, AuctionMenuHolder holder) {
        UUID playerId = player.getUniqueId();
        if (!refreshesInProgress.add(playerId)) return;
        
        long requestVersion = holder.incrementAndGetRequestVersion();
        loadAndRender(player, holder.viewType(), holder.currency(), holder.page(), holder.sort(), holder.sellerFilter(), holder.searchFilter(), holder.category(), holder, requestVersion, () -> refreshesInProgress.remove(playerId));
    }

    public void removeRefreshProgress(UUID playerId) {
        refreshesInProgress.remove(playerId);
    }

    private void loadAndRender(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, AuctionMenuHolder targetHolder, long expectedVersion, Runnable onFinished) {
        int pageSize = configManager.getConfigValues().auctionSlots().size();
        String playerName = player.getName();
        int maxStorageDays = configManager.getConfigValues().maxAuctionStorageDays();

        String effectiveSellerFilter = sellerFilter;
        String effectiveCategory = category;
        String effectiveSearch = searchFilter;
        AuctionSort effectiveSort = sort;

        if (viewType == AuctionViewType.SELLING || viewType == AuctionViewType.EXPIRED) {
            effectiveSellerFilter = playerName;
            effectiveCategory = null;
            effectiveSearch = null;
            effectiveSort = AuctionSort.NEWEST;
        }

        String finalSellerFilter = effectiveSellerFilter;
        String finalCategory = effectiveCategory;
        String finalSearch = effectiveSearch;
        AuctionSort finalSort = effectiveSort;

        scheduler.runAsyncThenSync(
            () -> {
                try {
                    AuctionRepository.MenuData menuData = repository.loadMenuData(currency, playerName, maxStorageDays, page, pageSize, finalSort, finalCategory, finalSellerFilter, finalSearch);
                    return new LoadResult(menuData.items(), menuData.totalCount(), menuData.sellingCount(), menuData.expiredCount(), null, expectedVersion);
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка загрузки аукциона: " + e.getMessage());
                    return new LoadResult(null, 0, 0, 0, e, expectedVersion);
                }
            },
            data -> {
                try {
                    if (data.error != null) {
                        if (player.isOnline()) {
                            player.sendMessage("§cОшибка открытия аукциона.");
                        }
                        return;
                    }
                    
                    if (!player.isOnline()) return;

                    if (targetHolder != null && data.requestVersion != targetHolder.getRequestVersion()) {
                        return;
                    }
                    
                    if (targetHolder != null) {
                        builder.refreshLotDisplays(targetHolder.getInventory(), targetHolder, data.items, data.totalCount, pageSize);
                    } else {
                        Inventory inventory = builder.buildMainMenu(player, viewType, currency, page, 
                                finalSort, finalSellerFilter, finalSearch, finalCategory, 
                                data.items, data.totalCount, pageSize, data.sellingCount, data.expiredCount);
                        player.openInventory(inventory);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка открытия меню: " + e.getMessage());
                    if (player.isOnline()) {
                        player.sendMessage("§cОшибка открытия аукциона.");
                    }
                } finally {
                    if (onFinished != null) onFinished.run();
                }
            }
        );
    }

    private void loadAndRender(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, AuctionMenuHolder targetHolder, Runnable onFinished) {
        loadAndRender(player, viewType, currency, page, sort, sellerFilter, searchFilter, category, targetHolder, -1L, onFinished);
    }
    
    private record LoadResult(List<AuctionItem> items, int totalCount, int sellingCount, int expiredCount, Exception error, long requestVersion) {}
}