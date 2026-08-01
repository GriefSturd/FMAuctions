package ru.moscow.foxkiss.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.gui.builder.MenuBuilder;

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

    private final Set<UUID> refreshesInProgress = Collections.newSetFromMap(new HashMap<>());

    public AuctionMenu(FMAuction plugin, IConfigManager configManager, AuctionRepository repository, PlayerPreferences playerPreferences) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.playerPreferences = playerPreferences;

        this.itemFactory = new ItemDisplayFactory(plugin, configManager);
        this.builder = new MenuBuilder(configManager, itemFactory);
        this.quantityController = new QuantityMenuController(configManager, itemFactory, builder);
        this.confirmController = new ConfirmMenuController(configManager, itemFactory, builder);
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

    public void openQuantity(Player player, AuctionCurrency currency, long lotId) {
        Optional<AuctionItem> optItem = repository.findById(lotId);
        if (optItem.isEmpty()) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
            return;
        }
        quantityController.openQuantity(player, currency, optItem.get(), 1);
    }

    public void openConfirm(Player player, AuctionCurrency currency, long lotId, int amount) {
        Optional<AuctionItem> optItem = repository.findById(lotId);
        if (optItem.isEmpty()) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
            return;
        }
        confirmController.openConfirm(player, currency, optItem.get(), amount);
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
        loadAndRender(player, holder.viewType(), holder.currency(), holder.page(), holder.sort(),
                holder.sellerFilter(), holder.searchFilter(), holder.category(), holder,
                () -> refreshesInProgress.remove(playerId));
    }

    public void removeRefreshProgress(UUID playerId) {
        refreshesInProgress.remove(playerId);
    }

    private void loadAndRender(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category, AuctionMenuHolder targetHolder, Runnable onFinished) {
        try {
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

            List<AuctionItem> items = repository.findPage(currency, page, pageSize, effectiveSort, effectiveCategory, effectiveSellerFilter, effectiveSearch);
            int total = repository.countAll(currency, effectiveCategory, effectiveSellerFilter, effectiveSearch);
            int totalPages = (int) Math.ceil((double) total / pageSize);

            int sellingCount = repository.countSellingByPlayer(currency, playerName);
            int expiredCount = repository.countExpiredByPlayer(currency, playerName, maxStorageDays);

            if (targetHolder != null) {
                builder.refreshLotDisplays(targetHolder.getInventory(), targetHolder, items);
                if (onFinished != null) onFinished.run();
                return;
            }

            Inventory inventory = builder.buildMainMenu(player, viewType, currency, page, effectiveSort, effectiveSellerFilter, effectiveSearch, effectiveCategory, items, sellingCount, expiredCount);
            player.openInventory(inventory);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка открытия меню: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§cОшибка открытия аукциона.");
        } finally {
            if (onFinished != null) onFinished.run();
        }
    }
}