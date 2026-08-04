package ru.moscow.foxkiss.auction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;

@Getter
@RequiredArgsConstructor
public enum AuctionSort {
    NEWEST(Comparator.comparingLong(AuctionItem::getCreatedAt).reversed()),
    OLDEST(Comparator.comparingLong(AuctionItem::getCreatedAt)),
    EXPENSIVE(Comparator.comparingDouble(AuctionItem::getPrice).reversed()),
    CHEAP(Comparator.comparingDouble(AuctionItem::getPrice)),
    EXPENSIVE_PER_ITEM(Comparator.comparingDouble(AuctionItem::pricePerItem).reversed()),
    CHEAP_PER_ITEM(Comparator.comparingDouble(AuctionItem::pricePerItem));

    private final Comparator<AuctionItem> comparator;

    public AuctionSort next() {
        AuctionSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public AuctionSort previous() {
        AuctionSort[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}