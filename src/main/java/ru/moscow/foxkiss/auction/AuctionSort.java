package ru.moscow.foxkiss.auction;

import java.util.Comparator;

public enum AuctionSort {
    NEWEST,
    OLDEST,
    EXPENSIVE,
    CHEAP,
    EXPENSIVE_PER_ITEM,
    CHEAP_PER_ITEM;

    private static final AuctionSort[] VALUES = values();

    private static final Comparator<AuctionItem> NEWEST_SORT = Comparator.comparingLong(AuctionItem::createdAt).reversed();
    private static final Comparator<AuctionItem> OLDEST_SORT = Comparator.comparingLong(AuctionItem::createdAt);
    private static final Comparator<AuctionItem> EXPENSIVE_SORT = Comparator.comparingDouble(AuctionItem::price).reversed();
    private static final Comparator<AuctionItem> CHEAP_SORT = Comparator.comparingDouble(AuctionItem::price);
    private static final Comparator<AuctionItem> EXPENSIVE_PER_ITEM_SORT = Comparator.comparingDouble(AuctionItem::pricePerItem).reversed();
    private static final Comparator<AuctionItem> CHEAP_PER_ITEM_SORT = Comparator.comparingDouble(AuctionItem::pricePerItem);

    public AuctionSort next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public AuctionSort previous() {
        return VALUES[(ordinal() - 1 + VALUES.length) % VALUES.length];
    }

    public Comparator<AuctionItem> comparator() {
        return switch (this) {
            case NEWEST -> NEWEST_SORT;
            case OLDEST -> OLDEST_SORT;
            case EXPENSIVE -> EXPENSIVE_SORT;
            case CHEAP -> CHEAP_SORT;
            case EXPENSIVE_PER_ITEM -> EXPENSIVE_PER_ITEM_SORT;
            case CHEAP_PER_ITEM -> CHEAP_PER_ITEM_SORT;
        };
    }
}