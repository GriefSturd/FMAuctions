package ru.moscow.foxkiss.auction.services;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.function.Consumer;

public final class AuctionBuyService extends BaseAuctionService {

    private final AuctionValidationService validationService;

    public AuctionBuyService(SchedulerService scheduler,IConfigManager configManager,AuctionRepository repository,AuctionValidationService validationService,AuctionTransactionService transactionService){
        super(scheduler,configManager,repository,transactionService);
        this.validationService = validationService;
    }

    public void buy(Player buyer,long lotId,int amount,Consumer<Boolean> callback) {
        scheduler.runAsync(() -> {
            if (!repository.markAsSelling(lotId)){
                finishWithMessage(buyer,config().messages().noId(),false,callback);
                return;
            }

            AuctionItem item = repository.findById(lotId).orElse(null);

            if (item == null){
                repository.restoreStatus(lotId);
                finishWithMessage(buyer,config().messages().noId(),false,callback);
                return;
            }

            if (!buyer.isOnline()) {
                restoreAndFinish(buyer,lotId,"",callback);
                return;
            }

            if (validationService.isOwner(buyer,item)) {
                restoreAndFinish(buyer,lotId,config().messages().noOwn(),callback);
                return;
            }

            if (validationService.isExpired(item)) {
                restoreAndFinish(buyer,lotId,config().messages().noId(),callback);
                return;
            }

            int buyAmount = Math.max(1,Math.min(amount,item.amount()));

            ItemStack bought = item.itemStackClone();
            bought.setAmount(buyAmount);

            if (!validationService.canFit(buyer,bought)) {
                restoreAndFinish(buyer,lotId,config().messages().inventoryFull(),callback);
                return;
            }

            double totalPrice = item.pricePerItem() * buyAmount;

            if (!validationService.hasEnoughMoney(buyer,item.currency(),totalPrice)) {
                restoreAndFinish(buyer,lotId,config().messages().nomoney(),callback);
                return;
            }

            if (!transactionService.withdrawMoney(buyer,item.currency(),totalPrice)) {
                restoreAndFinish(buyer,lotId,config().messages().nomoney(),callback);
                return;
            }

            boolean deleted = repository.delete(lotId);

            if (!deleted) {
                transactionService.depositMoney(buyer,item.currency(),totalPrice);
                restoreStatusAsync(lotId);
                finishWithMessage(buyer,config().messages().noId(),false,callback);
                return;
            }

            scheduler.runSync(() -> completePurchase(buyer,item,buyAmount,totalPrice,callback));
        });
    }


    private void completePurchase(Player buyer,AuctionItem item,int buyAmount,double totalPrice,Consumer<Boolean> callback) {

        ItemStack bought = item.itemStackClone();
        bought.setAmount(buyAmount);

        transactionService.giveItem(buyer,bought);

        int left = item.amount() - buyAmount;

        if (left > 0){
            transactionService.createRemainingLot(item,left);
        }

        OfflinePlayer seller = Bukkit.getOfflinePlayer(item.sellerName());

        transactionService.depositMoney(seller,item.currency(),totalPrice);

        transactionService.recordSale(item.sellerName(),buyer.getName(),item.currency(),bought.getType().name(),buyAmount,totalPrice);

        String itemName = ItemUtils.getItemDisplayName(bought);
        String priceStr = PriceFormatter.format(totalPrice);
        String symbol = item.currency().symbol(config());

        buyer.sendMessage(PlaceholderUtils.applypapi(buyer,config().messages().yspex().replace("{symbol_value}",priceStr + " " + symbol),configManager));

        Player onlineSeller = Bukkit.getPlayer(item.sellerName());

        if(onlineSeller != null){
            onlineSeller.sendMessage(PlaceholderUtils.applypapi(onlineSeller,
                    config().messages().buySeller()
                            .replace("{buyer}",buyer.getName())
                            .replace("{item_name}",itemName)
                            .replace("{amount}",String.valueOf(buyAmount))
                            .replace("{price}",priceStr)
                            .replace("{symbol_value}",symbol),configManager));
        }

        if (callback != null) callback.accept(true);
    }
}