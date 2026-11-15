package ru.moscow.foxkiss.gui;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.builder.MenuBuilder;
import ru.moscow.foxkiss.gui.menu.AuctionPages;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.holders.InventoryMenuHolder;
import ru.moscow.foxkiss.gui.holders.QuantityMenuHolder;
import ru.moscow.foxkiss.gui.holders.SalesHistoryMenuHolder;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.List;
import java.util.UUID;

public final class AuctionMenu {
    private final AuctionPages pages;
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final PlayerPreferences playerPreferences;
    private final MenuBuilder builder;
    private final QuantityMenuController quantityController;
    private final ConfirmMenuController confirmController;
    private final ItemDisplayFactory itemFactory;
    private final SchedulerService scheduler;
    private final SalesHistoryMenu salesHistoryMenu;
    private final ProfileMenu profileMenu;
    private final ProfileInputService profileInputService;

    public AuctionMenu(FMAuction plugin, IConfigManager configManager, AuctionRepository repository, PlayerPreferences playerPreferences, ItemDisplayFactory itemFactory, SchedulerService scheduler, ProfileInputService profileInputService) {
        this.configManager = configManager;
        this.repository = repository;
        this.playerPreferences = playerPreferences;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
        this.builder = new MenuBuilder(configManager, itemFactory);
        this.pages = new AuctionPages(plugin, configManager, repository, playerPreferences, builder, scheduler);
        this.quantityController = new QuantityMenuController(configManager, itemFactory, builder);
        this.confirmController = new ConfirmMenuController(configManager, itemFactory, builder);
        this.salesHistoryMenu = new SalesHistoryMenu(plugin, configManager, repository, itemFactory, scheduler);
        this.profileMenu = new ProfileMenu(configManager, repository, itemFactory, scheduler);
        this.profileInputService = profileInputService;
    }

    public void openInventoryView(Player player, AuctionCurrency currency, long lotId) {
        scheduler.runAsync(() -> {
            AuctionItem lot = repository.findById(lotId).orElse(null);
            List<Long> ids = repository.loadInventoryLotIds(currency);
            scheduler.runSync(() -> {
                if (!player.isOnline()) return;
                if (lot == null) {
                    sendMessage(player, configManager.getConfigValues().messages().noId());
                    return;
                }
                int total = ids.size();
                int current = ids.indexOf(lotId);
                if (current < 0) current = 0;
                long prevId = current > 0 ? ids.get(current - 1) : -1L;
                long nextId = current < total - 1 ? ids.get(current + 1) : -1L;

                String title = configManager.getConfigValues().inventorySelling().view().title().replace("{current}", String.valueOf(current + 1)).replace("{max}", String.valueOf(Math.max(1, total)));

                InventoryMenuHolder holder = new InventoryMenuHolder(currency, player.getUniqueId(), lotId, prevId, nextId, current, total);
                Inventory inv = Bukkit.createInventory(holder, 54, TextUtils.component(title));
                holder.setInventory(inv);
                builder.buildInventoryView(holder, lot, current, total);
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

    public void openMaterialOffers(Player player, AuctionMenuHolder source, String material) {
        if (source.getCurrency() != AuctionCurrency.VAULT) return;
        openInventory(player, AuctionViewType.OFFERS, source.getCurrency(), 0, source.getSort(), source.getSellerFilter(), source.getSearchFilter(), material);
    }

    public void openSalesHistory(Player player, AuctionCurrency currency, int page) {
        salesHistoryMenu.open(player, currency, page);
    }

    public void openProfile(Player player, AuctionCurrency currency) {
        profileMenu.open(player, currency);
    }

    public void openProfileInput(Player player, boolean discord) {
        profileInputService.open(player, discord);
    }

    public void openSeller(Player player, AuctionCurrency currency, String query) {
        scheduler.runAsync(() -> {
            String seller = repository.findProfileOwner(query).orElse(query);
            scheduler.runSync(() -> {
                if (player.isOnline()) {
                    openMain(player, currency, 0, null, seller, null, "all");
                }
            });
        });
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
                    sendMessage(player, configManager.getConfigValues().messages().noId());
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
                    sendMessage(player, configManager.getConfigValues().messages().noId());
                    return;
                }
                confirmController.openConfirm(player, currency, item, amount, isInventoryBuy);
            });
        });
    }

    public void openInventory(Player player, AuctionViewType viewType, AuctionCurrency currency, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        pages.open(player, viewType, currency, page, sort, sellerFilter, searchFilter, category);
    }

    public boolean isRefreshing(Player player) { return pages.isRefreshing(player); }

    public void refreshInventory(Player player, AuctionMenuHolder holder) {
        if (holder instanceof SalesHistoryMenuHolder) {
            openSalesHistory(player, holder.getCurrency(), holder.getPage());
        } else {
            pages.refreshInventory(player, holder);
        }
    }

    public void refreshAfterTake(Player player, AuctionMenuHolder holder, int slot, long lotId) {
        pages.refreshAfterTake(player, holder, slot, lotId);
    }

    public void onPlayerQuit(UUID uuid) {
        pages.onPlayerQuit(uuid);
        playerPreferences.remove(uuid);
    }

    public void updateQuantityDisplay(Inventory inventory, QuantityMenuHolder holder, AuctionItem item) {
        quantityController.updateQuantityDisplay(inventory, holder, item);
    }

    public void reload() {
        itemFactory.clearCache();
        builder.buildGlassCache();
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(TextUtils.component(PlaceholderUtils.apply(sender, message, configManager)));
    }
}