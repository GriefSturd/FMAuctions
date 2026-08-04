package ru.moscow.foxkiss.auction;

import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.gui.AuctionViewType;  // <-- добавлен импорт

import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    void init();

    long create(String sellerName, AuctionCurrency currency, ItemStack itemStack, double price);

    List<AuctionItem> findPage(AuctionCurrency currency, int page, int size, AuctionSort sort, String category, String sellerFilter, String searchFilter);

    Optional<AuctionItem> findById(long id);

    MenuData loadMenuData(AuctionCurrency currency, String playerName, int maxDays, int page, int pageSize, AuctionSort sort, String category, String sellerFilter, String searchFilter, AuctionViewType viewType);

    boolean delete(long id);

    void recordSale(String sellerName, String buyerName, AuctionCurrency currency, String itemType, int amount, double price);

    List<TopSeller> getTopSellers(AuctionCurrency currency, int limit);

    PlayerStats getPlayerStats(String playerName, AuctionCurrency currency);

    List<String> getUniqueMaterialNames(AuctionCurrency currency);

    boolean markAsSelling(long id);

    void restoreStatus(long id);

    int countActiveBySellerSince(String sellerName, AuctionCurrency currency, long since);

    void close();

    record TopSeller(String name, int soldCount, double totalEarned) {}
    record PlayerStats(int soldCount, double totalEarned) {}
    record MenuData(List<AuctionItem> items, int totalCount, int sellingCount, int expiredCount) {}
}