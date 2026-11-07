package ru.moscow.foxkiss.auction.services;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.utils.ItemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record AuctionValidationService(IConfigManager configManager, EconomyProvider economyProvider) {

    public boolean isEconomyAvailable(AuctionCurrency currency) {
        return !economyProvider.available(currency);
    }

    public boolean isValidPrice(double price, AuctionCurrency currency) {
        if (price <= 0) return true;
        if (currency != null && currency.isIntegerOnly()) {
            return !(Math.abs(price - Math.round(price)) < 1e-6);
        }
        return false;
    }

    public boolean isPriceInRange(double price, AuctionCurrency currency, boolean donate) {

        ConfigValues.PriceLimits limits = configManager.getConfigValues().priceLimits();

        double min;
        double max;

        if (currency == AuctionCurrency.VAULT || currency == AuctionCurrency.PLAYER_POINTS) {
            min = donate ? limits.minPriceMoneyDauc() : limits.minPriceMoneyAuc();
            max = donate ? limits.maxPriceMoneyDauc() : limits.maxPriceMoneyAuc();
        } else {
            min = 0.01;
            max = Double.MAX_VALUE;
        }

        return !(price >= min) || !(price <= max);
    }

    public double getMinPrice(AuctionCurrency currency, boolean donate) {
        if (currency == AuctionCurrency.VAULT || currency == AuctionCurrency.PLAYER_POINTS)
            return donate ? configManager.getConfigValues().priceLimits().minPriceMoneyDauc() : configManager.getConfigValues().priceLimits().minPriceMoneyAuc();

        return 0.01;
    }

    public double getMaxPrice(AuctionCurrency currency, boolean donate) {
        if (currency == AuctionCurrency.VAULT || currency == AuctionCurrency.PLAYER_POINTS)
            return donate ? configManager.getConfigValues().priceLimits().maxPriceMoneyDauc() : configManager.getConfigValues().priceLimits().maxPriceMoneyAuc();

        return Double.MAX_VALUE;
    }

    public boolean isSellableItem(ItemStack item) {
        return ItemUtils.isSellable(item);
    }

    public boolean canFit(Player player, ItemStack item) {
        Inventory inv = player.getInventory();

        int needed = item.getAmount();
        int stackLimit = Math.min(item.getMaxStackSize(), inv.getMaxStackSize());

        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                needed -= stackLimit;
            } else if (slot.isSimilar(item)) {
                int free = Math.min(slot.getMaxStackSize(), inv.getMaxStackSize()) - slot.getAmount();
                if (free > 0) needed -= free;
            }
            if (needed <= 0) return true;
        }

        return false;
    }


    public boolean isExpired(AuctionItem item) {
        return item.expired(configManager.getConfigValues().maxAuctionStorageDays());
    }

    public boolean hasEnoughMoney(Player player, AuctionCurrency currency, double amount) {
        return !economyProvider.has(player, currency, amount);
    }

    public boolean isOwner(Player player, AuctionItem item) {
        return player.getName().equalsIgnoreCase(item.sellerName());
    }
}
