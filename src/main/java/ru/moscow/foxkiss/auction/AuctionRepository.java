package ru.moscow.foxkiss.auction;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface AuctionRepository {

    void init();

    CompletableFuture<Long> create(String sellerName, AuctionCurrency currency, ItemStack itemStack, double price);

    default CompletableFuture<Long> createIfAllowed(String sellerName, AuctionCurrency currency,
                                                    ItemStack itemStack, double price, int maxDays, int limit) {
        return create(sellerName, currency, itemStack, price);
    }

    CompletableFuture<List<AuctionItem>> findAll(AuctionCurrency currency);

    CompletableFuture<Optional<AuctionItem>> findById(long id);

    CompletableFuture<Boolean> delete(long id);

    CompletableFuture<Void> recordSale(String sellerName, String buyerName, AuctionCurrency currency,
                                       String itemType, int amount, double price);

    CompletableFuture<List<TopSeller>> getTopSellers(AuctionCurrency currency, int limit);

    CompletableFuture<PlayerStats> getPlayerStats(String playerName, AuctionCurrency currency);

    CompletableFuture<List<String>> getUniqueMaterialNames(AuctionCurrency currency);

    CompletableFuture<Boolean> markAsSelling(long id);
    CompletableFuture<Void> restoreStatus(long id);

    void close();

    record SalesStats(int totalSales, double totalMoney) {}
    record TopSeller(String name, int soldCount, double totalEarned) {}
    record PlayerStats(int soldCount, double totalEarned) {}
}