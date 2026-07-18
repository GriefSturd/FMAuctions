package ru.moscow.foxkiss.auction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.TimeUnit;

public final class AuctionItem {

    private final long id;
    private final String sellerName;
    private final AuctionCurrency currency;
    private final ItemStack itemStack;
    private final double price;
    private final long createdAt;

    public AuctionItem(long id, String sellerName, AuctionCurrency currency, ItemStack itemStack, double price, long createdAt) {
        this.id = id;
        this.sellerName = sellerName;
        this.currency = currency;
        this.itemStack = itemStack;
        this.price = price;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public String sellerName() {
        return sellerName;
    }

    public AuctionCurrency currency() {
        return currency;
    }

    public ItemStack itemStackClone() {
        return itemStack.clone();
    }

    public Material material() {
        return itemStack.getType();
    }

    public int amount() {
        return itemStack.getAmount();
    }

    public double price() {
        return price;
    }

    public long createdAt() {
        return createdAt;
    }

    public double pricePerItem() {
        int amount = itemStack.getAmount();
        if (amount <= 0) return price;
        return price / amount;
    }

    public boolean expired(int maxDays) {
        long expireTime = createdAt + TimeUnit.DAYS.toMillis(maxDays);
        return System.currentTimeMillis() >= expireTime;
    }
}