package ru.moscow.foxkiss.auction.services;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.function.Consumer;

public final class AuctionBuyService extends BaseAuctionService {

    private final AuctionValidationService validationService;
    private final ItemDisplayFactory itemFactory;

    public AuctionBuyService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, AuctionValidationService validationService, AuctionTransactionService transactionService, ItemDisplayFactory itemFactory) {
        super(scheduler, configManager, repository, transactionService);
        this.validationService = validationService;
        this.itemFactory = itemFactory;
    }

    public void buy(Player buyer, long lotId, int amount, Consumer<Boolean> callback) {
        scheduler.runAsync(() -> {
            if (!repository.markAsSelling(lotId)) {
                scheduler.runSync(() -> finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), false, callback));
                return;
            }

            AuctionItem item = repository.findById(lotId).orElse(null);
            if (item == null) {
                repository.restoreStatus(lotId);
                scheduler.runSync(() -> finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), false, callback));
                return;
            }

            scheduler.runSync(() -> processBuy(buyer, item, amount, lotId, callback));
        });
    }

    private void processBuy(Player buyer, AuctionItem item, int amount, long lotId, Consumer<Boolean> callback) {
        if (!buyer.isOnline()) {
            transactionService.restoreStatus(lotId);
            if (callback != null) callback.accept(false);
            return;
        }

        if (validationService.isOwner(buyer, item)) {
            restoreAndFinish(buyer, lotId, configManager.getConfigValues().messages().noOwn(), callback);
            return;
        }

        if (validationService.isExpired(item)) {
            restoreAndFinish(buyer, lotId, configManager.getConfigValues().messages().noId(), callback);
            return;
        }

        int buyAmount = Math.max(1, Math.min(amount, item.amount()));
        ItemStack bought = item.getItemStack().clone();
        bought.setAmount(buyAmount);

        if (!validationService.canFit(buyer, bought)) {
            restoreAndFinish(buyer, lotId, configManager.getConfigValues().messages().inventoryFull(), callback);
            return;
        }

        double totalPrice = item.pricePerItem() * buyAmount;
        if (!validationService.hasEnoughMoney(buyer, item.getCurrency(), totalPrice)) {
            restoreAndFinish(buyer, lotId, configManager.getConfigValues().messages().nomoney(), callback);
            return;
        }

        if (!transactionService.withdrawMoney(buyer, item.getCurrency(), totalPrice)) {
            restoreAndFinish(buyer, lotId, configManager.getConfigValues().messages().nomoney(), callback);
            return;
        }

        scheduler.runAsync(() -> {
            boolean deleted = repository.delete(lotId);
            scheduler.runSync(() -> {
                if (!deleted) {
                    transactionService.depositMoney(buyer, item.getCurrency(), totalPrice);
                    transactionService.restoreStatus(lotId);
                    finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), false, callback);
                    return;
                }
                completePurchase(buyer, item, buyAmount, totalPrice, callback);
            });
        });
    }

    private void completePurchase(Player buyer, AuctionItem item, int buyAmount, double totalPrice, Consumer<Boolean> callback) {
        ItemStack bought = item.getItemStack().clone();
        bought.setAmount(buyAmount);
        transactionService.giveItem(buyer, bought);

        int left = item.amount() - buyAmount;
        if (left > 0) {
            transactionService.createRemainingLot(item, left);
            itemFactory.invalidateLotCache(item.getId());
        }

        itemFactory.invalidateLotCache(item.getId());

        OfflinePlayer seller = Bukkit.getOfflinePlayer(item.getSellerName());
        transactionService.depositMoney(seller, item.getCurrency(), totalPrice);
        transactionService.recordSale(item.getSellerName(), buyer.getName(), item.getCurrency(),
                bought.getType().name(), buyAmount, totalPrice);

        String itemName = ItemUtils.getItemDisplayName(bought);
        String priceStr = PriceFormatter.format(totalPrice);
        String symbol = item.getCurrency().symbol(configManager.getConfigValues());

        buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().yspex().replace("{symbol_value}", priceStr + " " + symbol), configManager));

        Player onlineSeller = Bukkit.getPlayer(item.getSellerName());
        if (onlineSeller != null) {
            onlineSeller.sendMessage(PlaceholderUtils.applypapi(onlineSeller,
                    configManager.getConfigValues().messages().buySeller()
                            .replace("{buyer}", buyer.getName())
                            .replace("{item_name}", itemName)
                            .replace("{amount}", String.valueOf(buyAmount))
                            .replace("{price}", priceStr)
                            .replace("{symbol_value}", symbol),
                    configManager));
        }

        if (callback != null) callback.accept(true);
    }
}