package ru.moscow.foxkiss.gui;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.builder.MenuBuilder;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.holders.InventoryMenuHolder;
import ru.moscow.foxkiss.gui.holders.QuantityMenuHolder;
import ru.moscow.foxkiss.gui.ConfirmMenuController;
import ru.moscow.foxkiss.gui.QuantityMenuController;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuctionMenu {
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final PlayerPreferences playerPreferences;
    private final MenuBuilder builder;
    private final QuantityMenuController quantityController;
    private final ConfirmMenuController confirmController;
    private final ItemDisplayFactory itemFactory;
    private final SchedulerService scheduler;

    private final Object2ObjectOpenHashMap<UUID, Boolean> requestInProgress = new Object2ObjectOpenHashMap<>();

    public AuctionMenu(JavaPlugin plugin, IConfigManager configManager, AuctionRepository repository, PlayerPreferences playerPreferences, ItemDisplayFactory itemFactory) {
        this.configManager = configManager;
        this.repository = repository;
        this.playerPreferences = playerPreferences;
        this.scheduler = new SchedulerService(plugin);
        this.itemFactory = itemFactory;
        this.builder = new MenuBuilder(configManager, itemFactory, repository);
        this.quantityController = new QuantityMenuController(configManager, itemFactory, builder);
        this.confirmController = new ConfirmMenuController(configManager, itemFactory, builder);
    }

    public void openInventoryView(Player player, AuctionCurrency currency, long lotId) {
        scheduler.runAsync(() -> {
            AuctionItem lot = repository.findById(lotId).orElse(null);
            List<Long> ids = repository.loadInventoryLotIds(currency);
            scheduler.runSync(() -> {
                if (!player.isOnline()) return;
                if (lot == null) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
                    return;
                }
                int total = ids.size();
                int current = ids.indexOf(lotId);
                if (current < 0) current = 0;
                long prevId = current > 0 ? ids.get(current - 1) : -1L;
                long nextId = current < total - 1 ? ids.get(current + 1) : -1L;

                String title = configManager.getConfigValues().inventorySelling().view().title();

                InventoryMenuHolder holder = new InventoryMenuHolder(currency, player.getUniqueId(), lotId, prevId, nextId, current, total);
                Inventory inv = Bukkit.createInventory(holder, 54, TextUtils.colorize(title));
                holder.setInventory(inv);
                builder.buildInventoryView(holder, currency, lot, current, total);
                player.openInventory(inv);
            });
        });
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
        scheduler.runAsync(() -> {
            AuctionItem item = repository.findById(lotId).orElse(null);
            scheduler.runSync(() -> {
                if (!player.isOnline()) return;
                if (item == null) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
                    return;
                }
                quantityController.openQuantity(player, currency, item, 1);
            });
        });
    }

    public void openConfirm(Player player, AuctionCurrency currency, long lotId, int amount, boolean isInventoryBuy) {
        scheduler.runAsync(() -> {
            AuctionItem item = repository.findById(lotId).orElse(null);
            scheduler.runSync(() -> {
                if (!player.isOnline()) return;
                if (item == null) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
                    return;
                }
                confirmController.openConfirm(player, currency, item, amount, isInventoryBuy);
            });
        });
    }

    public void openInventory(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        renderNew(player, viewType, currency, page, sort, sellerFilter, searchFilter, category);
    }

    public void refreshInventory(Player player, AuctionMenuHolder holder) {
        UUID uuid = player.getUniqueId();
        synchronized (requestInProgress) {
            if (requestInProgress.getOrDefault(uuid, false)) {
                return;
            }
            requestInProgress.put(uuid, true);
        }
        long requestVersion = holder.incrementAndGetRequestVersion();
        renderRefresh(player, holder, requestVersion);
    }

    public void updateQuantityDisplay(Inventory inventory, QuantityMenuHolder holder, AuctionItem item) {
        quantityController.updateQuantityDisplay(inventory, holder, item);
    }

    public void reload() {
        itemFactory.clearCache();
        builder.buildGlassCache();
    }

    private void renderNew(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        UUID uuid = player.getUniqueId();
        synchronized (requestInProgress) {
            if (requestInProgress.getOrDefault(uuid, false)) {
                return;
            }
            requestInProgress.put(uuid, true);
        }

        int pageSize = configManager.getConfigValues().gui(currency).auctionSlots().size();
        String playerName = player.getName();
        int maxStorageDays = configManager.getConfigValues().maxAuctionStorageDays();

        if (viewType == AuctionViewType.SELLING || viewType == AuctionViewType.EXPIRED) {
            sellerFilter = playerName;
            category = null;
            searchFilter = null;
            sort = AuctionSort.NEWEST;
        }

        final String fSeller = sellerFilter;
        final String fCategory = category;
        final String fSearch = searchFilter;
        final AuctionSort fSort = sort;

        scheduler.runAsyncThenSync(
                () -> {
                    AuctionRepository.MenuData data = repository.loadMenuData(
                            currency, playerName, maxStorageDays, page, pageSize,
                            fSort, fCategory, fSeller, fSearch, viewType
                    );
                    return new LoadResult(data.items(), data.totalCount(), data.sellingCount(), data.expiredCount());
                },
                result -> {
                    try {
                        Inventory inv = builder.buildMainMenu(
                                player, viewType, currency, page,
                                fSort, fSeller, fSearch, fCategory,
                                result.items, result.totalCount, pageSize,
                                result.sellingCount, result.expiredCount
                        );
                        player.openInventory(inv);
                    } finally {
                        synchronized (requestInProgress) {
                            requestInProgress.remove(uuid);
                        }
                    }
                }
        );
    }

    private void renderRefresh(Player player, AuctionMenuHolder holder, long requestVersion) {
        UUID uuid = player.getUniqueId();
        AuctionCurrency currency = holder.getCurrency();

        int pageSize = configManager.getConfigValues().gui(currency).auctionSlots().size();
        String playerName = player.getName();
        int maxStorageDays = configManager.getConfigValues().maxAuctionStorageDays();

        AuctionViewType viewType = holder.getViewType();
        int page = holder.getPage();
        AuctionSort sort = holder.getSort();
        String sellerFilter = holder.getSellerFilter();
        String searchFilter = holder.getSearchFilter();
        String category = holder.getCategory();

        if (viewType == AuctionViewType.SELLING || viewType == AuctionViewType.EXPIRED) {
            sellerFilter = playerName;
            category = null;
            searchFilter = null;
            sort = AuctionSort.NEWEST;
        }

        final String fSeller = sellerFilter;
        final String fCategory = category;
        final String fSearch = searchFilter;
        final AuctionSort fSort = sort;

        scheduler.runAsyncThenSync(
                () -> {
                    AuctionRepository.MenuData data = repository.loadMenuData(
                            currency, playerName, maxStorageDays, page, pageSize,
                            fSort, fCategory, fSeller, fSearch, viewType
                    );
                    return new LoadResult(data.items(), data.totalCount(), data.sellingCount(), data.expiredCount());
                },
                result -> {
                    try {
                        if (requestVersion != holder.getRequestVersion()) return;

                        builder.refreshLotDisplays(holder.getInventory(), holder, result.items, result.totalCount, pageSize);

                        if (viewType == AuctionViewType.MAIN) {
                            builder.refreshNavigationButtons(holder.getInventory(), holder);
                        } else {
                            ConfigValues values = configManager.getConfigValues();
                            Map<Integer, ConfigValues.GlassPane> panes = (viewType == AuctionViewType.SELLING)
                                    ? values.gui(holder.getCurrency()).mainGlass()
                                    : values.gui(holder.getCurrency()).expiredGlass();
                            builder.fillGlass(holder.getInventory(), panes);
                        }
                    } finally {
                        synchronized (requestInProgress) {
                            requestInProgress.remove(uuid);
                        }
                    }
                }
        );
    }

    private record LoadResult(List<AuctionItem> items, int totalCount, int sellingCount, int expiredCount) {}
}