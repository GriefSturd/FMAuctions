package ru.moscow.foxkiss.auction.services;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.Optional;
import java.util.function.Consumer;

public final class AuctionBuyService {

    private final SchedulerService scheduler;
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final AuctionValidationService validationService;
    private final AuctionTransactionService transactionService;

    public AuctionBuyService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, AuctionValidationService validationService, AuctionTransactionService transactionService) {
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.repository = repository;
        this.validationService = validationService;
        this.transactionService = transactionService;
    }

    public void buy(Player buyer, long lotId, int amount, Consumer<Boolean> callback) {
        scheduler.runAsyncThenSync(
            () -> {
                if (!repository.markAsSelling(lotId)) {
                    return null;
                }
                AuctionItem item = repository.findById(lotId).orElse(null);
                if (item == null) {
                    repository.restoreStatus(lotId);
                }
                return item;
            },
            item -> {
                if (item == null) {
                    finish(buyer, callback, false, configManager.getConfigValues().messages().noId());
                    return;
                }
                processBuy(buyer, item, amount, lotId, callback);
            }
        );
    }

    private void processBuy(Player buyer, AuctionItem item, int amount, long lotId, Consumer<Boolean> callback) {
        if (!buyer.isOnline()) {
            transactionService.restoreStatusAsync(lotId);
            callback.accept(false);
            return;
        }

        if (validationService.isOwner(buyer, item)) {
            restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().noOwn());
            return;
        }

        if (validationService.isExpired(item)) {
            restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().noId());
            return;
        }

        int buyAmount = Math.max(1, Math.min(amount, item.amount()));
        ItemStack bought = item.itemStackClone();
        bought.setAmount(buyAmount);

        if (!validationService.canFit(buyer, bought)) {
            restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().inventoryFull());
            return;
        }

        double totalPrice = item.pricePerItem() * buyAmount;
        if (!validationService.hasEnoughMoney(buyer, item.currency(), totalPrice)) {
            restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().nomoney());
            return;
        }

        if (!transactionService.withdrawMoney(buyer, item.currency(), totalPrice)) {
            restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().nomoney());
            return;
        }

        scheduler.runAsyncThenSync(
            () -> repository.delete(lotId),
            deleted -> completePurchase(buyer, item, buyAmount, totalPrice, deleted, callback)
        );
    }

    private void completePurchase(Player buyer, AuctionItem item, int buyAmount, double totalPrice, boolean deleted, Consumer<Boolean> callback) {
        if (!buyer.isOnline()) {
            if (deleted) transactionService.depositMoney(buyer, item.currency(), totalPrice);
            callback.accept(false);
            return;
        }

        if (!deleted) {
            transactionService.depositMoney(buyer, item.currency(), totalPrice);
            transactionService.restoreStatusAsync(item.id());
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().noId(), configManager));
            callback.accept(false);
            return;
        }

        finishPurchase(buyer, item, buyAmount, totalPrice, callback);
    }

    private void finishPurchase(Player buyer, AuctionItem item, int buyAmount, double totalPrice, Consumer<Boolean> callback) {
        try {
            ItemStack bought = item.itemStackClone();
            bought.setAmount(buyAmount);
            
            transactionService.giveItem(buyer, bought);

            int leftAmount = item.amount() - buyAmount;
            if (leftAmount > 0) {
                transactionService.createRemainingLot(item, leftAmount);
            }

            OfflinePlayer seller = Bukkit.getOfflinePlayer(item.sellerName());
            transactionService.depositMoney(seller, item.currency(), totalPrice);
            transactionService.recordSale(item.sellerName(), buyer.getName(), item.currency(),
                    bought.getType().name(), buyAmount, totalPrice);

            String itemDisplayName = ItemUtils.getItemDisplayName(bought);
            String priceStr = PriceFormatter.format(totalPrice);
            String symbol = item.currency().symbol(configManager.getConfigValues());
            
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer,
                    configManager.getConfigValues().messages().yspex()
                            .replace("{symbol_value}", priceStr + " " + symbol), configManager));

            Player onlineSeller = Bukkit.getPlayer(item.sellerName());
            if (onlineSeller != null) {
                onlineSeller.sendMessage(PlaceholderUtils.applypapi(onlineSeller,
                        configManager.getConfigValues().messages().buySeller()
                                .replace("{buyer}", buyer.getName())
                                .replace("{item_name}", itemDisplayName)
                                .replace("{amount}", String.valueOf(buyAmount))
                                .replace("{price}", priceStr)
                                .replace("{symbol_value}", symbol),
                        configManager));
            }
            
            callback.accept(true);

        } catch (Exception e) {
            transactionService.depositMoney(buyer, item.currency(), totalPrice);
            transactionService.restoreStatusAsync(item.id());
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer,
                    configManager.getConfigValues().messages().databaseError(), configManager));
            callback.accept(false);
        }
    }

    private void finish(Player player, Consumer<Boolean> callback, boolean result, String message) {
        scheduler.runSync(() -> {
            if (player.isOnline()) {
                player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
            }
            callback.accept(result);
        });
    }

    private void restoreAndFinish(Player player, long lotId, Consumer<Boolean> callback, boolean result, String message) {
        scheduler.runAsync(() -> {
            repository.restoreStatus(lotId);
            scheduler.runSync(() -> {
                if (player.isOnline()) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                }
                callback.accept(result);
            });
        });
    }
}
