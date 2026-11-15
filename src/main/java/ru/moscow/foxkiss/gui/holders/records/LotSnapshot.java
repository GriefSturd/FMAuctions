package ru.moscow.foxkiss.gui.holders.records;

import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionLotType;
import ru.moscow.foxkiss.auction.AuctionStatus;
import ru.moscow.foxkiss.auction.PlayerProfile;

import java.util.Objects;

public record LotSnapshot(
        long id, int amount, double price, long createdAt,
        AuctionLotType type, AuctionStatus status, String seller,
        String discord, String store, long sales, long ageBucket, long daysLeft) {

    public boolean matches(AuctionItem item, PlayerProfile profile, long ageBucket, long daysLeft) {
        return this.ageBucket == ageBucket
                && this.daysLeft == daysLeft
                && this.amount == item.amount()
                && Double.compare(this.price, item.price()) == 0
                && this.createdAt == item.createdAt()
                && this.type == item.type()
                && this.status == item.status()
                && this.sales == profile.sales()
                && Objects.equals(this.seller, item.sellerName())
                && Objects.equals(this.discord, profile.discord())
                && Objects.equals(this.store, profile.storeName());
    }
}