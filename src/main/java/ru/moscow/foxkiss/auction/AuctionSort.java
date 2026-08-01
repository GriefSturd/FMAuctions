package ru.moscow.foxkiss.auction;

import java.util.Comparator;

public enum AuctionSort {
    NEWEST(Comparator.comparingLong(AuctionItem::createdAt).reversed()),
    OLDEST(Comparator.comparingLong(AuctionItem::createdAt)),
    EXPENSIVE(Comparator.comparingDouble(AuctionItem::price).reversed()),
    CHEAP(Comparator.comparingDouble(AuctionItem::price)),
    EXPENSIVE_PER_ITEM(Comparator.comparingDouble(AuctionItem::pricePerItem).reversed()),
    CHEAP_PER_ITEM(Comparator.comparingDouble(AuctionItem::pricePerItem));

    private final Comparator<AuctionItem> comparator;

    AuctionSort(Comparator<AuctionItem> comparator) {
        this.comparator = comparator;
    }

    public AuctionSort next() {
        AuctionSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public AuctionSort previous() {
        AuctionSort[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public Comparator<AuctionItem> comparator() {
        return comparator;
    }
}