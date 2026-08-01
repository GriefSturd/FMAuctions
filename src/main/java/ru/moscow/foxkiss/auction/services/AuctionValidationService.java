package ru.moscow.foxkiss.auction.services;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.utils.ItemUtils;

public final class AuctionValidationService {

    private final IConfigManager configManager;
    private final EconomyProvider economyProvider;

    public AuctionValidationService(IConfigManager configManager, EconomyProvider economyProvider) {
        this.configManager = configManager;
        this.economyProvider = economyProvider;
    }

    public boolean isEconomyAvailable(AuctionCurrency currency) {
        return economyProvider.available(currency);
    }

    public boolean isValidPrice(double price) {
        return price > 0;
    }

    public boolean isPriceInRange(double price, AuctionCurrency currency, boolean isDonateAuction) {
        var priceLimits = configManager.getConfigValues().priceLimits();
        
        if (currency == AuctionCurrency.VAULT) {
            if (isDonateAuction) {
                return price >= priceLimits.minPriceMoneyDauc() && price <= priceLimits.maxPriceMoneyDauc();
            } else {
                return price >= priceLimits.minPriceMoneyAuc() && price <= priceLimits.maxPriceMoneyAuc();
            }
        }
        
        return true;
    }
    
    public double getMinPrice(AuctionCurrency currency, boolean isDonateAuction) {
        var priceLimits = configManager.getConfigValues().priceLimits();
        
        if (currency == AuctionCurrency.VAULT) {
            return isDonateAuction ? priceLimits.minPriceMoneyDauc() : priceLimits.minPriceMoneyAuc();
        }
        
        return 0.01;
    }
    
    public double getMaxPrice(AuctionCurrency currency, boolean isDonateAuction) {
        var priceLimits = configManager.getConfigValues().priceLimits();
        
        if (currency == AuctionCurrency.VAULT) {
            return isDonateAuction ? priceLimits.maxPriceMoneyDauc() : priceLimits.maxPriceMoneyAuc();
        }
        
        return Double.MAX_VALUE;
    }

    public boolean isSellableItem(ItemStack item) {
        return ItemUtils.isSellable(item);
    }

    public boolean canFit(Player player, ItemStack item) {
        Inventory inv = player.getInventory();
        int needed = item.getAmount();
        ItemStack template = item.clone();
        int emptySlots = 0;

        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                emptySlots++;
                continue;
            }
            if (slot.isSimilar(template)) {
                int free = slot.getMaxStackSize() - slot.getAmount();
                if (free > 0) {
                    int take = Math.min(free, needed);
                    needed -= take;
                    if (needed <= 0) return true;
                }
            }
        }

        int maxStack = template.getMaxStackSize();
        int slotsNeeded = (needed + maxStack - 1) / maxStack;
        return emptySlots >= slotsNeeded;
    }

    public boolean isExpired(AuctionItem item) {
        return item.expired(configManager.getConfigValues().maxAuctionStorageDays());
    }

    public boolean hasEnoughMoney(Player player, AuctionCurrency currency, double amount) {
        return economyProvider.has(player, currency, amount);
    }

    public boolean isOwner(Player player, AuctionItem item) {
        return item.sellerName().equalsIgnoreCase(player.getName());
    }
}
