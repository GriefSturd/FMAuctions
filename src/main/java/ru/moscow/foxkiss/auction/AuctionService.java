package ru.moscow.foxkiss.auction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.services.AuctionBuyService;
import ru.moscow.foxkiss.auction.services.AuctionSellInventoryService;
import ru.moscow.foxkiss.auction.services.AuctionSellService;
import ru.moscow.foxkiss.auction.services.AuctionTakeService;
import ru.moscow.foxkiss.auction.services.AuctionTransactionService;
import ru.moscow.foxkiss.auction.services.AuctionValidationService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.List;
import java.util.function.Consumer;

public record AuctionService(IConfigManager configManager, AuctionSellService sellService, AuctionSellInventoryService sellInventoryService, AuctionBuyService buyService, AuctionTakeService takeService, AuctionRepository repository, AuctionTransactionService transactionService, SchedulerService scheduler) {

    public static AuctionService create(FMAuction plugin, IConfigManager configManager, AuctionRepository repository, EconomyProvider economyProvider, LimitService limitService, ItemDisplayFactory itemFactory, SchedulerService scheduler) {
        AuctionValidationService validationService = new AuctionValidationService(configManager, economyProvider);
        AuctionTransactionService transactionService = new AuctionTransactionService(scheduler, repository, economyProvider, itemFactory);

        AuctionSellService sellService = new AuctionSellService(plugin, scheduler, configManager, repository, limitService, validationService, transactionService);
        AuctionSellInventoryService sellInventoryService = new AuctionSellInventoryService(plugin,scheduler, configManager, repository, limitService, validationService, transactionService);

        AuctionBuyService buyService = new AuctionBuyService(plugin, scheduler, configManager, repository, validationService, transactionService, itemFactory);

        AuctionTakeService takeService = new AuctionTakeService(scheduler, configManager, repository, validationService, transactionService, itemFactory);

        return new AuctionService(configManager, sellService, sellInventoryService, buyService, takeService, repository, transactionService, scheduler);
    }

    public void sell(Player player, AuctionCurrency currency, double price) {
        sellService.sell(player, currency, price);
    }

    public void sellInventory(Player player, AuctionCurrency currency, double price) {
        sellInventoryService.sellInventory(player, currency, price);
    }

    public void buy(Player buyer, long lotId, int amount, Consumer<Boolean> callback) {
        buyService.buy(buyer, lotId, amount, callback);
    }

    public void buyInventory(Player buyer, long lotId, Consumer<Boolean> callback) {
        buyService.buyInventory(buyer, lotId, callback);
    }

    public void take(Player player, long lotId, Consumer<Boolean> callback) {
        takeService.take(player, lotId, callback);
    }

    public void processPendingDeliveries(Player player) {
        String playerName = player.getName();
        scheduler.runAsync(() -> {
            List<PendingDelivery> pending = repository.getPendingDeliveries(playerName);
            if (pending == null || pending.isEmpty()) return;

            scheduler.runSync(() -> {
                if (!player.isOnline()) return;

                for (PendingDelivery delivery : pending) {
                    if (delivery.lotType() == AuctionLotType.INVENTORY) {
                        List<ItemStack> contents = delivery.inventoryContents();
                        if (contents != null) {
                            for (ItemStack c : contents) {
                                if (c == null || c.getType() == Material.AIR) continue;
                                transactionService.giveItem(player, c.clone());
                            }
                        }
                    } else {
                        if (delivery.item() != null && delivery.item().getType() != Material.AIR) {
                            transactionService.giveItem(player, delivery.item().clone());
                        }
                    }
                    long deliveryId = delivery.id();
                    scheduler.runAsync(() -> repository.removePendingDelivery(deliveryId));
                }
            });
        });
    }

    public void processPendingPayouts(Player player) {
        String playerName = player.getName();
        scheduler.runAsync(() -> {
            List<PendingPayout> pending = repository.getPendingPayouts(playerName);
            if (pending == null || pending.isEmpty()) return;

            for (PendingPayout payout : pending) {
                if (transactionService.depositMoney(player, payout.currency(), payout.amount())) {
                    long payoutId = payout.id();
                    scheduler.runAsync(() -> repository.removePendingPayout(payoutId));
                }
            }
        });
    }

    public void takePendingInventoryItems(Player player, AuctionCurrency currency) {
        String playerName = player.getName();
        scheduler.runAsync(() -> {
            List<ItemStack> pending = repository.getPendingInventoryItems(playerName, currency);
            if (pending.isEmpty()) {
                scheduler.runSync(() -> player.sendMessage(PlaceholderUtils.applypapi(player,
                        configManager.getConfigValues().messages().pendingItemsEmpty(), configManager)));
                return;
            }

            scheduler.runSync(() -> {
                if (!player.isOnline()) return;

                List<ItemStack> leftovers = transactionService.giveItems(player, pending);
                int received = countStacks(pending) - countStacks(leftovers);
                int remaining = countStacks(leftovers);
                List<ItemStack> stored = List.copyOf(leftovers);
                scheduler.runAsync(() -> {
                    if (!repository.replacePendingInventoryItems(playerName, currency, stored)) {
                        scheduler.runSync(() -> player.sendMessage(PlaceholderUtils.applypapi(player,
                                configManager.getConfigValues().messages().databaseError(), configManager)));
                        return;
                    }

                    scheduler.runSync(() -> {
                        if (!player.isOnline()) return;
                        if (received > 0) {
                            player.sendMessage(PlaceholderUtils.applypapi(player,
                                    configManager.getConfigValues().messages().pendingItemsReceived()
                                            .replace("{amount}", String.valueOf(received)), configManager));
                        }
                        if (remaining > 0) {
                            player.sendMessage(PlaceholderUtils.applypapi(player,
                                    configManager.getConfigValues().messages().pendingItemsRemaining()
                                            .replace("{amount}", String.valueOf(remaining))
                                            .replace("{command}", currency == AuctionCurrency.VAULT ? "/ah items" : "/dah items"), configManager));
                        }
                    });
                });
            });
        });
    }

    private int countStacks(List<ItemStack> items) {
        int amount = 0;

        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                amount++;
            }
        }

        return amount;
    }
}