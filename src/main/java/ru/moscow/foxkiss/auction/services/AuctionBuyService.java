package ru.moscow.foxkiss.auction.services;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.util.List;
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

    public void buyInventory(Player buyer, long lotId, Consumer<Boolean> callback) {
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

            scheduler.runSync(() -> processBuyInventory(buyer, item, lotId, callback));
        });
    }

    private void processBuyInventory(Player buyer, AuctionItem item, long lotId, Consumer<Boolean> callback) {
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

        List<ItemStack> contents = item.getInventoryContents();
        if (contents == null || contents.isEmpty()) {
            restoreAndFinish(buyer, lotId, configManager.getConfigValues().messages().noId(), callback);
            return;
        }

        if (!canFitAll(buyer, contents)) {
            restoreAndFinish(buyer, lotId, configManager.getConfigValues().messages().inventoryFull(), callback);
            return;
        }

        double totalPrice = item.getPrice();
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
                completeInventoryPurchase(buyer, item, totalPrice, callback);
            });
        });
    }

    private boolean canFitAll(Player player, List<ItemStack> contents) {
        org.bukkit.inventory.Inventory tmp = Bukkit.createInventory(null, 36);
        tmp.setContents(player.getInventory().getStorageContents());
        java.util.ArrayList<ItemStack> toGive = new java.util.ArrayList<>();
        for (ItemStack c : contents) if (c != null && c.getType() != Material.AIR) toGive.add(c.clone());
        if (toGive.isEmpty()) return true;
        java.util.Map<Integer, ItemStack> left = tmp.addItem(toGive.toArray(new ItemStack[0]));
        return left.isEmpty();
    }

    private void completeInventoryPurchase(Player buyer, AuctionItem item, double totalPrice, Consumer<Boolean> callback) {
        List<ItemStack> contents = item.getInventoryContents();
        for (ItemStack content : contents) {
            if (content == null || content.getType() == Material.AIR) continue;
            transactionService.giveItem(buyer, content.clone());
        }

        itemFactory.invalidateLotCache(item.getId());

        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(item.getSellerName());
        transactionService.depositMoney(sellerOffline, item.getCurrency(), totalPrice);
        transactionService.recordSale(item.getSellerName(), buyer.getName(), item.getCurrency(), "INVENTORY", 1, totalPrice);

        int totalItems = 0;
        for (ItemStack content : contents) if (content != null && content.getType() != Material.AIR) totalItems += content.getAmount();

        String str = PriceFormatter.format(totalPrice);
        String ys = item.getCurrency().symbol(configManager.getConfigValues());

        buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().yspex()
                .replace("{item_name}", item.getSellerName())
                .replace("{symbol_value}", str + ys), configManager));

        Player seller = Bukkit.getPlayer(item.getSellerName());
        if (seller != null) {
            seller.sendMessage(PlaceholderUtils.applypapi(seller,
                    configManager.getConfigValues().messages().buySeller()
                            .replace("{buyer}", buyer.getName())
                            .replace("{item_name}", "Инвентарь")
                            .replace("{amount}", String.valueOf(totalItems))
                            .replace("{price}", str)
                            .replace("{symbol_value}", ys),
                    configManager));
        }

        if (callback != null) callback.accept(true);
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
        ItemStack b = item.getItemStack().clone();
        b.setAmount(buyAmount);

        if (!validationService.canFit(buyer, b)) {
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
        ItemStack b = item.getItemStack().clone();
        b.setAmount(buyAmount);
        transactionService.giveItem(buyer, b);

        int left = item.amount() - buyAmount;
        if (left > 0) {
            transactionService.createRemainingLot(item, left);
            itemFactory.invalidateLotCache(item.getId());
        }

        itemFactory.invalidateLotCache(item.getId());

        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(item.getSellerName());
        transactionService.depositMoney(sellerOffline, item.getCurrency(), totalPrice);
        transactionService.recordSale(item.getSellerName(), buyer.getName(), item.getCurrency(), b.getType().name(), buyAmount, totalPrice);

        String i = ItemUtils.getItemDisplayName(b);

        String str = PriceFormatter.format(totalPrice);
        String ys = item.getCurrency().symbol(configManager.getConfigValues());

        buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().yspex().replace("{item_name}", i).replace("{symbol_value}", str + ys), configManager));

        Player seller = Bukkit.getPlayer(item.getSellerName());
        if (seller != null) {
            seller.sendMessage(PlaceholderUtils.applypapi(seller,
                    configManager.getConfigValues().messages().buySeller()
                            .replace("{buyer}", buyer.getName())
                            .replace("{item_name}", i)
                            .replace("{amount}", String.valueOf(buyAmount))
                            .replace("{price}", str)
                            .replace("{symbol_value}", ys),
                    configManager));
        }

        if (callback != null) callback.accept(true);
    }
}