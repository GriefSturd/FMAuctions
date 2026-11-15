package ru.moscow.foxkiss.gui.menu;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.gui.PlayerPreferences;
import ru.moscow.foxkiss.gui.builder.MenuBuilder;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.scheduler.SchedulerService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class AuctionPages {
    private final FMAuction plugin;
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final PlayerPreferences playerPreferences;
    private final MenuBuilder builder;
    private final SchedulerService scheduler;

    public AuctionPages(FMAuction plugin, IConfigManager configManager, AuctionRepository repository, PlayerPreferences playerPreferences, MenuBuilder builder, SchedulerService scheduler) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.playerPreferences = playerPreferences;
        this.builder = builder;
        this.scheduler = scheduler;
    }

    private final Map<UUID, TakeRefresh> takeRefreshes = new HashMap<>();
    private record TakeRefresh(AuctionMenuHolder holder, BukkitTask task) {}
    private final Object2LongMap<UUID> requestStartTime = new Object2LongOpenHashMap<>();

    public boolean isRefreshing(Player player) {
        UUID uuid = player.getUniqueId();
        Long started = requestStartTime.get(uuid);
        if (started == null) return false;
        return System.nanoTime() - started <= 15_000_000_000L;
    }

    public void refreshAfterTake(Player player, AuctionMenuHolder holder, int slot, long lotId) {
        if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
        holder.incrementAndGetRequestVersion();
        if (Objects.equals(holder.getLot(slot), lotId)) {
            holder.getLotsBySlot().remove(slot);
            holder.getLotsAmountBySlot().remove(slot);
            holder.getInventoryLotBySlot().remove(slot);
            holder.getLotSnapshots().remove(slot);
            holder.getInventory().setItem(slot, null);
        }
        scheduleTakeRefresh(player, holder);
    }

    private void scheduleTakeRefresh(Player player, AuctionMenuHolder holder) {
        UUID uuid = player.getUniqueId();
        TakeRefresh pending = takeRefreshes.get(uuid);
        if (pending != null) {
            if (pending.holder() == holder) return;
            pending.task().cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            takeRefreshes.remove(uuid);
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            refreshInventory(player, holder);
        }, 4L);
        takeRefreshes.put(uuid, new TakeRefresh(holder, task));
    }

    public void onPlayerQuit(UUID uuid) {
        TakeRefresh pending = takeRefreshes.remove(uuid);
        if (pending != null) pending.task().cancel();
        requestStartTime.remove(uuid);
    }


    public void refreshInventory(Player player, AuctionMenuHolder holder) {
        UUID uuid = player.getUniqueId();
        synchronized (requestStartTime) {
            if (requestStartTime.containsKey(uuid)) {
                long start = requestStartTime.getLong(uuid);
                if (System.nanoTime() - start <= 15_000_000_000L) {
                    return;
                }
                requestStartTime.removeLong(uuid);
            }
            requestStartTime.put(uuid, System.nanoTime());
        }
        if (holder.getViewType() == AuctionViewType.MAIN) {
            playerPreferences.setSort(uuid, holder.getCurrency(), holder.getSort());
            playerPreferences.setCategory(uuid, holder.getCurrency(), holder.getCategory());
        }
        long requestVersion = holder.incrementAndGetRequestVersion();
        renderRefresh(player, holder, requestVersion);
    }

    private boolean isStaleRequest(UUID uuid, long token) {
        synchronized (requestStartTime) {
            long current = requestStartTime.getOrDefault(uuid, -1L);
            return current != token;
        }
    }
    private void releaseRequest(UUID uuid, long token) {
        synchronized (requestStartTime) {
            if (!requestStartTime.containsKey(uuid) || requestStartTime.getLong(uuid) != token) return;
            requestStartTime.removeLong(uuid);
        }
    }

    public void open(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        UUID uuid = player.getUniqueId();
        synchronized (requestStartTime) {
            if (requestStartTime.containsKey(uuid)) {
                long start = requestStartTime.getLong(uuid);
                if (System.nanoTime() - start <= 15_000_000_000L) {
                    return;
                }
                requestStartTime.removeLong(uuid);
            }
            requestStartTime.put(uuid, System.nanoTime());
        }

        int pageSize = builder.getAuctionSlots(viewType, currency).size();
        if (pageSize == 0) {
            synchronized (requestStartTime) {
                requestStartTime.removeLong(uuid);
            }
            return;
        }
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

        final long token;
        synchronized (requestStartTime) { token = requestStartTime.getLong(uuid); }

        executeRequest(uuid, token, player, playerName, currency, viewType, page, pageSize, fSort, fSeller, fSearch, fCategory, maxStorageDays, (data) -> {
            Inventory inv = builder.buildMainMenu(player, viewType, currency, page, fSort, fSeller, fSearch, fCategory, data.items(), data.totalCount(), pageSize, data.sellingCount(), data.expiredCount(), data.profiles(), data.materialLots(), data.itemGroups());
            player.openInventory(inv);
        });
    }

    private void renderRefresh(Player player, AuctionMenuHolder holder, long requestVersion) {
        UUID uuid = player.getUniqueId();
        AuctionCurrency currency = holder.getCurrency();
        AuctionViewType viewType = holder.getViewType();
        int pageSize = builder.getAuctionSlots(viewType, currency).size();

        if (pageSize == 0) {
            synchronized (requestStartTime) {
                requestStartTime.removeLong(uuid);
            }
            return;
        }
        String playerName = player.getName();
        int maxStorageDays = configManager.getConfigValues().maxAuctionStorageDays();

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

        final long token;
        synchronized (requestStartTime) { token = requestStartTime.getLong(uuid); }

        executeRequest(uuid, token, player, playerName, currency, viewType, page, pageSize, fSort, fSeller, fSearch, fCategory, maxStorageDays, (data) -> {
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            if (requestVersion != holder.getRequestVersion()) return;

            builder.refreshLotDisplays(holder.getInventory(), holder, data.items(), data.totalCount(), pageSize, data.profiles(), data.materialLots(), data.itemGroups());
            builder.refreshNavigationButtons(holder.getInventory(), holder);
            builder.refreshCounters(holder.getInventory(), holder, data.sellingCount(), data.expiredCount());
        });
    }

    private void executeRequest(UUID uuid, long token, Player player, String playerName, AuctionCurrency currency, AuctionViewType viewType, int page, int pageSize, AuctionSort sort, String seller, String search, String category, int maxStorageDays, Consumer<AuctionRepository.MenuData> onSuccess) {
        scheduler.runAsync(() -> {
            try {
                AuctionRepository.MenuData data = repository.loadMenuData(currency, playerName, maxStorageDays, page, pageSize, sort, category, seller, search, viewType);

                scheduler.runSync(() -> {
                    try {
                        if (!player.isOnline() || isStaleRequest(uuid, token)) return;
                        onSuccess.accept(data);
                    } finally {
                        releaseRequest(uuid, token);
                    }
                });
            } catch (Exception e) {
                releaseRequest(uuid, token);
                plugin.getLogger().log(Level.WARNING, "Не удалось загрузить меню аукциона", e);
            }
        });
    }
}
