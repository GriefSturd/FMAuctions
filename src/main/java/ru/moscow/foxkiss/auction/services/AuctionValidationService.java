package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import java.util.List;
public record AuctionValidationService(IConfigManager configManager, EconomyProvider economyProvider) {

    public boolean isEconomyUnavailable(AuctionCurrency currency) {
        return !economyProvider.available(currency);
    }

    public boolean isInvalidPrice(double price, AuctionCurrency currency) {
        if (!Double.isFinite(price) || price <= 0) return true;
        if (currency == AuctionCurrency.PLAYER_POINTS && price > Integer.MAX_VALUE) return true;
        if (currency != null && currency.isIntegerOnly()) {
            return !(Math.abs(price - Math.round(price)) < 1e-6);
        }
        return false;
    }

    public boolean isPriceOutOfRange(double price, AuctionCurrency currency, boolean donate) {
        double min = getPriceLimit(currency, donate, true);
        double max = getPriceLimit(currency, donate, false);
        return !(price >= min) || !(price <= max);
    }

    public double getMinPrice(AuctionCurrency currency, boolean donate) {
        return getPriceLimit(currency, donate, true);
    }

    public double getMaxPrice(AuctionCurrency currency, boolean donate) {
        return getPriceLimit(currency, donate, false);
    }

    public boolean canFit(Player player, ItemStack item) {
        Inventory inv = player.getInventory();
        int stackLimit = Math.min(item.getMaxStackSize(), inv.getMaxStackSize());
        int needed = item.getAmount();

        ItemStack[] contents = inv.getStorageContents();
        for (ItemStack slot : contents) {
            if (slot == null || slot.isEmpty()) {
                needed -= stackLimit;
            } else if (slot.isSimilar(item)) {
                int free = stackLimit - slot.getAmount();
                if (free > 0) needed -= free;
            }

            if (needed <= 0) return true;
        }
        return false;
    }


    static boolean canFitAll(Player player, List<ItemStack> items) {
        Inventory inventory = player.getInventory();
        ItemStack[] slots = inventory.getStorageContents().clone();

        int[] amounts = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            ItemStack slot = slots[i];
            if (slot != null && !slot.isEmpty()) {
                amounts[i] = slot.getAmount();
            }
        }

        int inventoryLimit = inventory.getMaxStackSize();

        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;

            int limit = Math.min(inventoryLimit, item.getMaxStackSize());
            int remaining = item.getAmount();

            for (int i = 0; i < slots.length; i++) {
                if (remaining <= 0) break;

                if (amounts[i] == 0) {
                    int moved = Math.min(remaining, limit);
                    amounts[i] = moved;
                    remaining -= moved;
                    continue;
                }

                ItemStack slot = slots[i];
                if (amounts[i] < limit && slot != null && slot.isSimilar(item)) {
                    int moved = Math.min(remaining, limit - amounts[i]);
                    amounts[i] += moved;
                    remaining -= moved;
                }
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isExpired(AuctionItem item) {
        return item.expired(configManager.getConfigValues().maxAuctionStorageDays());
    }

    public boolean hasInsufficientFunds(Player player, AuctionCurrency currency, double amount) {
        return !economyProvider.has(player, currency, amount);
    }

    public boolean isOwner(Player player, AuctionItem item) {
        return player.getName().equalsIgnoreCase(item.sellerName());
    }

    private double getPriceLimit(AuctionCurrency currency, boolean donate, boolean min) {
        if (currency == AuctionCurrency.VAULT || currency == AuctionCurrency.PLAYER_POINTS) {
            ConfigValues.PriceLimits limits = configManager.getConfigValues().priceLimits();
            return min ? (donate ? limits.minPriceMoneyDauc() : limits.minPriceMoneyAuc())
                    : (donate ? limits.maxPriceMoneyDauc() : limits.maxPriceMoneyAuc());
        }
        return min ? 0.01 : Double.MAX_VALUE;
    }
}
