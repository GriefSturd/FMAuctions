package ru.moscow.foxkiss.gui.holders;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.gui.actions.Action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public abstract class AuctionMenuHolder implements InventoryHolder {

    protected AuctionViewType viewType;
    protected AuctionCurrency currency;
    protected final UUID viewer;
    protected int page;
    protected AuctionSort sort;
    protected String sellerFilter;
    protected String searchFilter;
    protected String category;

    protected final Map<Integer, Long> lotsBySlot = new HashMap<>();
    protected final Map<Integer, Integer> lotsAmountBySlot = new HashMap<>();
    protected final Map<Integer, Boolean> inventoryLotBySlot = new HashMap<>();
    protected final Map<Integer, List<Action>> actionsBySlot = new HashMap<>();

    protected int totalPages = 1;
    private long requestVersion = 0;
    private Inventory inventory;

    public AuctionMenuHolder(AuctionViewType viewType, AuctionCurrency currency, UUID viewer, int page, AuctionSort sort, String sellerFilter, String searchFilter, String category) {
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

    public void addLot(int slot, long id, int amount, boolean inventory) {
        lotsBySlot.put(slot, id);
        lotsAmountBySlot.put(slot, amount);
        inventoryLotBySlot.put(slot, inventory);
    }

    public void addLot(int slot, long id, int amount) {
        addLot(slot, id, amount, false);
    }

    public boolean isInventoryLot(int slot) {
        return Boolean.TRUE.equals(inventoryLotBySlot.get(slot));
    }

    public void addActions(int slot, List<Action> actions) {
        actionsBySlot.put(slot, actions);
    }

    public List<Action> getActions(int slot) {
        return actionsBySlot.get(slot);
    }

    public void clearLots() {
        lotsBySlot.clear();
        lotsAmountBySlot.clear();
        inventoryLotBySlot.clear();
    }

    public Long getLot(int slot) {
        return lotsBySlot.get(slot);
    }

    public Integer getLotAmount(int slot) {
        return lotsAmountBySlot.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}