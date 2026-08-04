package ru.moscow.foxkiss.gui.holders;

import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.gui.AuctionViewType;

import java.util.UUID;

public final class QuantityMenuHolder extends AuctionMenuHolder {
    private final long lotId;
    private int selectedAmount;
    private final int maxAmount;
    private final AuctionItem auctionItem;

    public QuantityMenuHolder(AuctionCurrency currency, UUID viewer, long lotId, int selectedAmount, int maxAmount, AuctionItem auctionItem) {
        super(AuctionViewType.QUANTITY, currency, viewer, 0, null, null, null, null);
        this.lotId = lotId;
        this.selectedAmount = selectedAmount;
        this.maxAmount = maxAmount;
        this.auctionItem = auctionItem;
    }

    public long lotId() {
        return lotId;
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

    public AuctionItem getAuctionItem() {
        return auctionItem;
    }
}