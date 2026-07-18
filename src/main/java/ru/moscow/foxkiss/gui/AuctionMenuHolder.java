package ru.moscow.foxkiss.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionSort;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AuctionMenuHolder implements InventoryHolder {

    private final AuctionViewType viewType;
    private final AuctionCurrency currency;
    private final UUID viewer;
    private final int page;
    private final AuctionSort sort;
    private final String sellerFilter;
    private final String searchFilter;
    private final long lotId;
    private final int maxAmount;
    private final int totalPages;
    private final String category;
    private final AuctionItem auctionItem;

    private int selectedAmount;
    private Inventory inventory;

    private final int confirmAmount;
    private final long confirmLotId;

    private final Map<Integer, Long> lotsBySlot = new HashMap<>();
    private final Map<Integer, Integer> lotsAmountBySlot = new HashMap<>(); // новое

    public AuctionMenuHolder(AuctionViewType viewType, AuctionCurrency currency, UUID viewer, int page,
                             AuctionSort sort, String sellerFilter, String searchFilter, long lotId,
                             int selectedAmount, int totalPages, String category, int maxAmount) {
        this(viewType, currency, viewer, page, sort, sellerFilter, searchFilter, lotId,
                selectedAmount, totalPages, category, maxAmount, null, 0, 0L);
    }

    public AuctionMenuHolder(AuctionViewType viewType, AuctionCurrency currency, UUID viewer, int page,
                             AuctionSort sort, String sellerFilter, String searchFilter, long lotId,
                             int selectedAmount, int totalPages, String category, int maxAmount,
                             AuctionItem auctionItem) {
        this(viewType, currency, viewer, page, sort, sellerFilter, searchFilter, lotId,
                selectedAmount, totalPages, category, maxAmount, auctionItem, 0, 0L);
    }

    public AuctionMenuHolder(AuctionViewType viewType, AuctionCurrency currency, UUID viewer, int page,
                             AuctionSort sort, String sellerFilter, String searchFilter, long lotId,
                             int selectedAmount, int totalPages, String category, int maxAmount,
                             int confirmAmount, long confirmLotId) {
        this(viewType, currency, viewer, page, sort, sellerFilter, searchFilter, lotId,
                selectedAmount, totalPages, category, maxAmount, null, confirmAmount, confirmLotId);
    }

    public AuctionMenuHolder(AuctionViewType viewType, AuctionCurrency currency, UUID viewer, int page,
                             AuctionSort sort, String sellerFilter, String searchFilter, long lotId,
                             int selectedAmount, int totalPages, String category, int maxAmount,
                             AuctionItem auctionItem, int confirmAmount, long confirmLotId) {
        this.viewType = viewType;
        this.currency = currency;
        this.viewer = viewer;
        this.page = page;
        this.sort = sort;
        this.sellerFilter = sellerFilter;
        this.searchFilter = searchFilter;
        this.lotId = lotId;
        this.selectedAmount = selectedAmount;
        this.totalPages = totalPages;
        this.category = category;
        this.maxAmount = maxAmount;
        this.auctionItem = auctionItem;
        this.confirmAmount = confirmAmount;
        this.confirmLotId = confirmLotId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public AuctionViewType viewType() {
        return viewType;
    }

    public AuctionCurrency currency() {
        return currency;
    }

    public AuctionSort sort() {
        return sort;
    }

    public String sellerFilter() {
        return sellerFilter;
    }

    public String searchFilter() {
        return searchFilter;
    }

    public long lotId() {
        return lotId;
    }

    public int page() {
        return page;
    }

    public int totalPages() {
        return totalPages;
    }

    public int selectedAmount() {
        return selectedAmount;
    }

    public void selectedAmount(int selectedAmount) {
        this.selectedAmount = selectedAmount;
    }

    public int maxAmount() {
        return maxAmount;
    }

    public String category() {
        return category;
    }

    public AuctionItem getAuctionItem() {
        return auctionItem;
    }

    public int confirmAmount() {
        return confirmAmount;
    }

    public long confirmLotId() {
        return confirmLotId;
    }

    public void addLot(int slot, long id, int amount) {
        lotsBySlot.put(slot, id);
        lotsAmountBySlot.put(slot, amount);
    }

    public Long getLot(int slot) {
        return lotsBySlot.get(slot);
    }

    public Integer getLotAmount(int slot) {
        return lotsAmountBySlot.get(slot);
    }
}