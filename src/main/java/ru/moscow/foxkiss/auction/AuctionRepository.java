package ru.moscow.foxkiss.auction;

import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.gui.AuctionViewType;

import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    long richi_limit = 0;

    void init();

    long create(String sellerName, AuctionCurrency currency, ItemStack itemStack, double price);

    long createWithinLimit(String sellerName, AuctionCurrency currency, ItemStack itemStack, double price, int limit);

    long createInventory(String sellerName, AuctionCurrency currency, ItemStack displayItem, List<ItemStack> contents, double price);

    long createInventoryWithinLimit(String sellerName, AuctionCurrency currency, ItemStack displayItem, List<ItemStack> contents, double price, int limit);

    Optional<AuctionItem> findById(long id);

    List<Long> loadInventoryLotIds(AuctionCurrency currency);

    MenuData loadMenuData(AuctionCurrency currency, String playerName, int maxDays, int page, int pageSize, AuctionSort sort, String category, String sellerFilter, String searchFilter, AuctionViewType viewType);

    boolean delete(long id);

    void recordSale(String sellerName, String buyerName, AuctionCurrency currency, String itemType, int amount, double price);

    SalesHistoryPage loadSalesHistory(String sellerName, AuctionCurrency currency, int page, int pageSize);

    List<TopSeller> getTopSellers(AuctionCurrency currency, int limit);

    PlayerStats getPlayerStats(String playerName, AuctionCurrency currency);

    List<String> getUniqueMaterialNames(AuctionCurrency currency);

    PlayerProfile getPlayerProfile(String playerName);

    void savePlayerProfile(String playerName, String discord, String storeName);

    Optional<String> findProfileOwner(String value);

    boolean markAsSelling(long id);

    boolean markAsProcessing(long id, String operationId, long startedAt);

    void restoreStatus(long id);

    void cancelProcessing(long id, String operationId);

    void logTransaction(String operationId, long lotId, String type, String buyerName, String sellerName, AuctionCurrency currency, int amount, double price, String status);

    void updateJournalStatus(String operationId, String status);

    PurchaseResult processPurchase(long lotId, String operationId, String buyerName, int buyAmount, double totalPrice);

    PurchaseResult processInventoryPurchase(long lotId, String operationId, String buyerName);

    TakeResult processTake(long lotId, String operationId, String playerName);

    void confirmDelivery(long pendingDeliveryId, String operationId);

    void removePendingDelivery(long pendingDeliveryId);

    List<PendingDelivery> getPendingDeliveries(String playerName);

    List<ItemStack> getPendingInventoryItems(String playerName, AuctionCurrency currency);

    boolean replacePendingInventoryItems(String playerName, AuctionCurrency currency, List<ItemStack> items);

    boolean completeInventoryDelivery(long pendingDeliveryId, String operationId, String playerName, AuctionCurrency currency, List<ItemStack> items);

    default boolean completeInventoryDeliveries(String playerName, List<DeliveryConfirmation> deliveries) {
        if (deliveries.size() != 1) throw new UnsupportedOperationException("Хранилище не поддерживает атомарное подтверждение пакета");
        DeliveryConfirmation delivery = deliveries.getFirst();
        return completeInventoryDelivery(delivery.id(), null, playerName, delivery.currency(), delivery.items());
    }

    void savePendingPayout(String playerName, AuctionCurrency currency, double amount);

    List<PendingPayout> getPendingPayouts(String playerName);

    void removePendingPayout(long payoutId);

    void processPendingPayouts(EconomyProvider economyProvider);

    void recoverStuckLots(EconomyProvider economyProvider, boolean forceAll);

    int countActiveBySellerSince(String sellerName, AuctionCurrency currency, long since);

    boolean prepareWithdrawal(String operationId);

    void refundPurchase(String operationId);

    void claimPendingInventoryItems(String playerName, AuctionCurrency currency);

    void savePendingDelivery(String playerName, AuctionCurrency currency, List<ItemStack> items);

    void close();

    record TopSeller(String name, int soldCount, double totalEarned) {}
    record DeliveryConfirmation(long id, AuctionCurrency currency, List<ItemStack> items) {}
    record PlayerStats(int soldCount, double totalEarned) {}
    record MenuData(List<AuctionItem> items, int totalCount, int sellingCount, int expiredCount, java.util.Map<String, PlayerProfile> profiles) {}
    record SalesHistoryPage(List<SaleHistoryEntry> entries, int totalCount, int page, int totalPages) {}
}
