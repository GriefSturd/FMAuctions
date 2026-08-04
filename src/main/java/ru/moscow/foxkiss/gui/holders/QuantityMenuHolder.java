package ru.moscow.foxkiss.gui.holders;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.gui.AuctionViewType;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
public final class QuantityMenuHolder extends AuctionMenuHolder {

    private final long lotId;

    @Setter
    private int selectedAmount;

    private final int maxAmount;
    private final AuctionItem auctionItem;

    public QuantityMenuHolder(AuctionCurrency currency, UUID viewer, long lotId, int selectedAmount, int maxAmount, AuctionItem auctionItem){
        super(AuctionViewType.QUANTITY, currency, viewer, 0, null, null, null, null);
        this.lotId = lotId;
        this.selectedAmount = selectedAmount;
        this.maxAmount = maxAmount;
        this.auctionItem = auctionItem;
    }
}