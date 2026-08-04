package ru.moscow.foxkiss.gui.holders;

import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.gui.AuctionViewType;

import java.util.UUID;

public final class ConfirmMenuHolder extends AuctionMenuHolder {
    private final long confirmLotId;
    private final int confirmAmount;

    public ConfirmMenuHolder(AuctionCurrency currency, UUID viewer, long confirmLotId, int confirmAmount) {
        super(AuctionViewType.CONFIRM, currency, viewer, 0, null, null, null, null);
        this.confirmLotId = confirmLotId;
        this.confirmAmount = confirmAmount;
    }

    public long confirmLotId() {
        return confirmLotId;
    }
    public int confirmAmount() {
        return confirmAmount;
    }
}