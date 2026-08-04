package ru.moscow.foxkiss.auction;

import lombok.Data;
import lombok.AllArgsConstructor;
import org.bukkit.inventory.ItemStack;

@Data
@AllArgsConstructor
public class AuctionItem {
    private final long id;
    private final String sellerName;
    private final AuctionCurrency currency;
    private final ItemStack itemStack;
    private final double price;
    private final long createdAt;
    private final AuctionStatus status;

    public int amount() {
        return itemStack.getAmount();
    }

    public double pricePerItem() {
        return price / Math.max(1, amount());
    }

    public boolean expired(int maxDays) {
        long diff = System.currentTimeMillis() - createdAt;
        return diff / 86_400_000L > maxDays;
    }

    public ItemStack itemStackClone() {
        return itemStack.clone();
    }
}