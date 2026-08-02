package ru.moscow.foxkiss.auction.services;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.scheduler.SchedulerService;

public final class AuctionTransactionService {

    private final SchedulerService scheduler;
    private final AuctionRepository repository;
    private final EconomyProvider economyProvider;

    public AuctionTransactionService(SchedulerService scheduler, AuctionRepository repository, EconomyProvider economyProvider) {
        this.scheduler = scheduler;
        this.repository = repository;
        this.economyProvider = economyProvider;
    }

    public boolean withdrawMoney(Player player, AuctionCurrency currency, double amount) {
        return economyProvider.withdraw(player, currency, amount);
    }

    public void depositMoney(OfflinePlayer player, AuctionCurrency currency, double amount) {
        economyProvider.deposit(player, currency, amount);
    }

    public void giveItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    public void removeItemFromHand(Player player) {
        player.getInventory().setItemInMainHand(null);
    }

    public void recordSale(String sellerName, String buyerName, AuctionCurrency currency, String itemType, int amount, double price) {
        scheduler.runAsync(() -> repository.recordSale(sellerName, buyerName, currency, itemType, amount, price));
    }

    public void createRemainingLot(AuctionItem originalItem, int remainingAmount) {
        ItemStack remaining = originalItem.itemStackClone();
        remaining.setAmount(remainingAmount);
        double remainingPrice = originalItem.pricePerItem() * remainingAmount;
        
        scheduler.runAsync(() -> repository.create(originalItem.sellerName(), originalItem.currency(), remaining, remainingPrice));
    }

    public void restoreStatusAsync(long lotId) {
        scheduler.runAsync(() -> repository.restoreStatus(lotId));
    }
}
