package ru.moscow.foxkiss.gui.holders;

import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.gui.AuctionViewType;

import java.util.UUID;

public final class MainMenuHolder extends AuctionMenuHolder {
    public MainMenuHolder(AuctionCurrency currency, UUID viewer, int page, AuctionSort sort,
                          String sellerFilter, String searchFilter, String category) {
        super(AuctionViewType.MAIN, currency, viewer, page, sort, sellerFilter, searchFilter, category);
    }
}