package ru.moscow.foxkiss.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.gui.AuctionViewType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public sealed abstract class AuctionMenuHolder implements InventoryHolder
        permits MainMenuHolder, QuantityMenuHolder, ConfirmMenuHolder {

    protected final AuctionViewType viewType;
    protected final AuctionCurrency currency;
    protected final UUID viewer;
    protected final int page;
    protected final AuctionSort sort;
    protected final String sellerFilter;
    protected final String searchFilter;
    protected final String category;
    protected final Map<Integer, Long> lotsBySlot = new HashMap<>();
    protected final Map<Integer, Integer> lotsAmountBySlot = new HashMap<>();
    protected int totalPages = 1;
    private long requestVersion = 0;
    private Inventory inventory;

    protected AuctionMenuHolder(AuctionViewType viewType, AuctionCurrency currency, UUID viewer, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
        this.viewType = viewType;
        this.currency = currency;
        this.viewer = viewer;
        this.page = page;
        this.sort = sort;
        this.sellerFilter = sellerFilter;
        this.searchFilter = searchFilter;
        this.category = category;
    }

    public long incrementAndGetRequestVersion() { return ++requestVersion; }
    public long getRequestVersion() { return requestVersion; }
    public int totalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public AuctionViewType viewType() { return viewType; }
    public AuctionCurrency currency() { return currency; }
    public AuctionSort sort() { return sort; }
    public String sellerFilter() { return sellerFilter; }
    public String searchFilter() { return searchFilter; }
    public String category() { return category; }
    public int page() { return page; }

    public void addLot(int slot, long id, int amount) {
        lotsBySlot.put(slot, id);
        lotsAmountBySlot.put(slot, amount);
    }

    public void clearLots() {
        lotsBySlot.clear();
        lotsAmountBySlot.clear();
    }

    public Long getLot(int slot) { return lotsBySlot.get(slot); }
    public Integer getLotAmount(int slot) { return lotsAmountBySlot.get(slot); }
}