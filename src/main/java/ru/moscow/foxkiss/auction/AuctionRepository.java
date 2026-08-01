package ru.moscow.foxkiss.auction;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository {

    void init();

    long create(String sellerName, AuctionCurrency currency, ItemStack itemStack, double price);

    List<AuctionItem> findAll(AuctionCurrency currency);

    Optional<AuctionItem> findById(long id);

    boolean delete(long id);

    void recordSale(String sellerName, String buyerName, AuctionCurrency currency, String itemType, int amount, double price);

    List<TopSeller> getTopSellers(AuctionCurrency currency, int limit);

    PlayerStats getPlayerStats(String playerName, AuctionCurrency currency);

    List<String> getUniqueMaterialNames(AuctionCurrency currency);

    boolean markAsSelling(long id);

    void restoreStatus(long id);

    void close();

    record TopSeller(String name, int soldCount, double totalEarned) {}
    record PlayerStats(int soldCount, double totalEarned) {}
}