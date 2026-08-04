package ru.moscow.foxkiss.auction.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.scheduler.SchedulerService;

import java.util.Map;

@Getter
@RequiredArgsConstructor
public final class AuctionTransactionService {

    private final SchedulerService scheduler;
    private final AuctionRepository repository;
    private final EconomyProvider economyProvider;
    private final ItemDisplayFactory itemFactory;

    public boolean withdrawMoney(Player player, AuctionCurrency currency, double amount) {
        return economyProvider.withdraw(player, currency, amount);
    }

    public void depositMoney(OfflinePlayer player, AuctionCurrency currency, double amount) {
        economyProvider.deposit(player, currency, amount);
    }

    public void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack left : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    public void removeItemFromHand(Player player) {
        player.getInventory().setItemInMainHand(null);
    }

    public void recordSale(String sellerName, String buyerName, AuctionCurrency currency, String itemType, int amount, double price) {
        scheduler.runAsync(() -> repository.recordSale(sellerName, buyerName, currency, itemType, amount, price));
    }

    public void createRemainingLot(AuctionItem original, int remainingAmount) {
        ItemStack remaining = original.getItemStack().clone();
        remaining.setAmount(remainingAmount);
        double totalPrice = original.pricePerItem() * remainingAmount;

        itemFactory.invalidateLotCache(original.getId());

        scheduler.runAsync(() -> repository.create(
                original.getSellerName(),
                original.getCurrency(),
                remaining,
                totalPrice
        ));
    }

    public void restoreStatus(long lotId) {
        scheduler.runAsync(() -> repository.restoreStatus(lotId));
    }
}