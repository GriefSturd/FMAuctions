
package ru.moscow.foxkiss.gui.holders.records;

import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionStatus;
import ru.moscow.foxkiss.auction.PlayerProfile;

import java.util.Objects;

public final class CachedLot {

    private final ItemStack display;

    private final int amount;
    private final double price;
    private final long createdAt;
    private final AuctionStatus status;
    private final AuctionCurrency currency;
    private final String seller;

    private final String discord;
    private final String store;
    private final long sales;

    private final long ageBucket;
    private final long daysLeft;

    public CachedLot(AuctionItem item, PlayerProfile profile, long ageBucket, long daysLeft, ItemStack display) {
        this.display = display;

        this.amount = item.amount();
        this.price = item.price();
        this.createdAt = item.createdAt();
        this.status = item.status();
        this.currency = item.currency();
        this.seller = item.sellerName();

        this.discord = profile.discord();
        this.store = profile.storeName();
        this.sales = profile.sales();

        this.ageBucket = ageBucket;
        this.daysLeft = daysLeft;
    }

    public boolean matches(AuctionItem item, PlayerProfile profile, long ageBucket, long daysLeft) {
        if (this.ageBucket != ageBucket
                || this.daysLeft != daysLeft) {
            return false;
        }

        return amount == item.amount()
                && Double.compare(price, item.price()) == 0
                && createdAt == item.createdAt()
                && status == item.status()
                && currency == item.currency()
                && sales == profile.sales()
                && Objects.equals(seller, item.sellerName())
                && Objects.equals(discord, profile.discord())
                && Objects.equals(store, profile.storeName());
    }

    public ItemStack display() {
        return display.clone();
    }
}
