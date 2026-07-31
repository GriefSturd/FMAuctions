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
    private int totalPages;
    private final String category;
    private final AuctionItem auctionItem;
    private final int confirmAmount;
    private final long confirmLotId;

    private int selectedAmount;
    private Inventory inventory;

    private final Map<Integer, Long> lotsBySlot = new HashMap<>();
    private final Map<Integer, Integer> lotsAmountBySlot = new HashMap<>();

    private AuctionMenuHolder(Builder builder) {
        this.viewType = builder.viewType;
        this.currency = builder.currency;
        this.viewer = builder.viewer;
        this.page = builder.page;
        this.sort = builder.sort;
        this.sellerFilter = builder.sellerFilter;
        this.searchFilter = builder.searchFilter;
        this.lotId = builder.lotId;
        this.selectedAmount = builder.selectedAmount;
        this.totalPages = builder.totalPages;
        this.category = builder.category;
        this.maxAmount = builder.maxAmount;
        this.auctionItem = builder.auctionItem;
        this.confirmAmount = builder.confirmAmount;
        this.confirmLotId = builder.confirmLotId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AuctionViewType viewType;
        private AuctionCurrency currency;
        private UUID viewer;
        private int page = 0;
        private AuctionSort sort = AuctionSort.NEWEST;
        private String sellerFilter = null;
        private String searchFilter = null;
        private long lotId = -1;
        private int selectedAmount = 1;
        private int totalPages = 1;
        private String category = null;
        private int maxAmount = 0;
        private AuctionItem auctionItem = null;
        private int confirmAmount = 0;
        private long confirmLotId = -1;

        public Builder viewType(AuctionViewType viewType) {
            this.viewType = viewType;
            return this;
        }

        public Builder currency(AuctionCurrency currency) {
            this.currency = currency;
            return this;
        }

        public Builder viewer(UUID viewer) {
            this.viewer = viewer;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder sort(AuctionSort sort) {
            this.sort = sort;
            return this;
        }

        public Builder sellerFilter(String sellerFilter) {
            this.sellerFilter = sellerFilter;
            return this;
        }

        public Builder searchFilter(String searchFilter) {
            this.searchFilter = searchFilter;
            return this;
        }

        public Builder lotId(long lotId) {
            this.lotId = lotId;
            return this;
        }

        public Builder selectedAmount(int selectedAmount) {
            this.selectedAmount = selectedAmount;
            return this;
        }

        public Builder totalPages(int totalPages) {
            this.totalPages = totalPages;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder maxAmount(int maxAmount) {
            this.maxAmount = maxAmount;
            return this;
        }

        public Builder auctionItem(AuctionItem auctionItem) {
            this.auctionItem = auctionItem;
            return this;
        }

        public Builder confirmAmount(int confirmAmount) {
            this.confirmAmount = confirmAmount;
            return this;
        }

        public Builder confirmLotId(long confirmLotId) {
            this.confirmLotId = confirmLotId;
            return this;
        }

        public AuctionMenuHolder build() {
            return new AuctionMenuHolder(this);
        }
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

    public void refreshLotsFrom(AuctionMenuHolder source) {
        lotsBySlot.clear();
        lotsBySlot.putAll(source.lotsBySlot);
        lotsAmountBySlot.clear();
        lotsAmountBySlot.putAll(source.lotsAmountBySlot);
        totalPages = source.totalPages;
    }

    public void clearLots() {
        lotsBySlot.clear();
        lotsAmountBySlot.clear();
    }

    public void totalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public Long getLot(int slot) {
        return lotsBySlot.get(slot);
    }

    public Integer getLotAmount(int slot) {
        return lotsAmountBySlot.get(slot);
    }
}
