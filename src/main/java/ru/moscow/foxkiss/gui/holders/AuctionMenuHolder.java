package ru.moscow.foxkiss.gui.holders;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.gui.AuctionViewType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
public sealed abstract class AuctionMenuHolder implements InventoryHolder permits MainMenuHolder, QuantityMenuHolder, ConfirmMenuHolder {

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

    @Setter
    protected int totalPages = 1;

    private long requestVersion = 0;

    @Setter
    private Inventory inventory;

    protected AuctionMenuHolder(AuctionViewType viewType,AuctionCurrency currency,UUID viewer,int page,AuctionSort sort,String sellerFilter,String searchFilter,String category){
        this.viewType = viewType;
        this.currency = currency;
        this.viewer = viewer;
        this.page = page;
        this.sort = sort;
        this.sellerFilter = sellerFilter;
        this.searchFilter = searchFilter;
        this.category = category;
    }

    public long incrementAndGetRequestVersion(){
        return ++requestVersion;
    }

    public long requestVersion(){
        return requestVersion;
    }

    @Override
    public @NotNull Inventory getInventory(){
        return inventory;
    }

    public void addLot(int slot,long id,int amount){
        lotsBySlot.put(slot,id);
        lotsAmountBySlot.put(slot,amount);
    }

    public void clearLots(){
        lotsBySlot.clear();
        lotsAmountBySlot.clear();
    }

    public Long getLot(int slot){
        return lotsBySlot.get(slot);
    }

    public Integer getLotAmount(int slot){
        return lotsAmountBySlot.get(slot);
    }
}