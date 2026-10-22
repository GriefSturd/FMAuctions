package ru.moscow.foxkiss.auction.services;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.PurchaseResult;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class AuctionBuyService extends BaseAuctionService {

    private final AuctionValidationService validationService;
    private final ItemDisplayFactory itemFactory;
    private final FMAuction plugin;

    public AuctionBuyService(FMAuction plugin, SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, AuctionValidationService validationService, AuctionTransactionService transactionService, ItemDisplayFactory itemFactory) {
        super(scheduler, configManager, repository, transactionService);
        this.plugin = plugin;
        this.validationService = validationService;
        this.itemFactory = itemFactory;
    }

    public void buy(Player buyer, long lotId, int amount, Consumer<Boolean> callback) {
        String operationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        scheduler.runAsync(() -> {
            if (!repository.markAsProcessing(lotId, operationId, now)) {
                scheduler.runSync(() -> finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback));
                return;
            }

            AuctionItem item = repository.findById(lotId).orElse(null);
            if (item == null) {
                repository.cancelProcessing(lotId, operationId);
                scheduler.runSync(() -> finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback));
                return;
            }

            repository.logTransaction(operationId, lotId, "BUY", buyer.getName(), item.sellerName(), item.currency(), amount, 0.0, "STARTED");
            scheduler.runSync(() -> processBuy(buyer, item, amount, lotId, operationId, callback));
        });
    }

    public void buyInventory(Player buyer, long lotId, Consumer<Boolean> callback) {
        String operationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        scheduler.runAsync(() -> {
            if (!repository.markAsProcessing(lotId, operationId, now)) {
                scheduler.runSync(() -> finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback));
                return;
            }

            AuctionItem item = repository.findById(lotId).orElse(null);
            if (item == null) {
                repository.cancelProcessing(lotId, operationId);
                scheduler.runSync(() -> finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback));
                return;
            }

            repository.logTransaction(operationId, lotId, "BUY_INVENTORY", buyer.getName(), item.sellerName(), item.currency(), 1, item.price(), "STARTED");
            scheduler.runSync(() -> processBuyInventory(buyer, item, lotId, operationId, callback));
        });
    }

    private void processBuyInventory(Player buyer, AuctionItem item, long lotId, String operationId, Consumer<Boolean> callback) {
        if (!buyer.isOnline()) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            if (callback != null) callback.accept(false);
            return;
        }

        if (validationService.isOwner(buyer, item)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().noOwn(), callback);
            return;
        }

        if (validationService.isExpired(item)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback);
            return;
        }

        List<ItemStack> contents = item.inventoryContents();
        if (contents == null || contents.isEmpty()) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback);
            return;
        }

        double totalPrice = item.price();
        if (!validationService.hasEnoughMoney(buyer, item.currency(), totalPrice)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().nomoney(), callback);
            return;
        }

        if (!transactionService.withdrawMoney(buyer, item.currency(), totalPrice)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().nomoney(), callback);
            return;
        }

        scheduler.runAsync(() -> {
            repository.updateJournalStatus(operationId, "MONEY_WITHDRAWN");
            PurchaseResult result = repository.processInventoryPurchase(lotId, operationId, buyer.getName());
            List<ItemStack> pending = result.success()
                    ? repository.getPendingInventoryItems(buyer.getName(), item.currency())
                    : List.of();
            scheduler.runSync(() -> {
                if (!result.success()) {
                    boolean refunded = transactionService.depositMoney(buyer, item.currency(), totalPrice);
                    if (!refunded) {
                        repository.savePendingPayout(buyer.getName(), item.currency(), totalPrice);
                        plugin.getLogger().warning("Не удалось вернуть средства покупателю " + buyer.getName() + ", возврат сохранён в ожидающие выплаты.");
                    }
                    repository.cancelProcessing(lotId, operationId);
                    repository.updateJournalStatus(operationId, "REFUNDED");
                    finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback);
                    return;
                }
                completeInventoryPurchase(buyer, item, totalPrice, result.pendingDeliveryId(), operationId, pending, callback);
            });
        });
    }

    private void completeInventoryPurchase(Player buyer, AuctionItem item, double totalPrice, long deliveryId, String operationId, List<ItemStack> pending, Consumer<Boolean> callback) {
        List<ItemStack> contents = item.inventoryContents();
        int automatic;

        List<ItemStack> leftovers = new ArrayList<>(transactionService.giveItems(buyer, pending));

        if (buyer.isOnline() && contents != null) {
            automatic = countStacks(pending) - countStacks(leftovers);
            leftovers.addAll(transactionService.giveItems(buyer, contents));

            int pendingAmount = countStacks(leftovers);
            int automaticAmount = automatic;
            List<ItemStack> stored = List.copyOf(leftovers);
            scheduler.runAsync(() -> {
                if (!repository.completeInventoryDelivery(deliveryId, operationId, buyer.getName(), item.currency(), stored)) return;

                scheduler.runSync(() -> {
                    if (!buyer.isOnline()) return;
                    if (automaticAmount > 0) {
                        buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().pendingItemsAutoReceived()
                                .replace("{amount}", String.valueOf(automaticAmount)), configManager));
                    }
                    if (pendingAmount > 0) {
                        buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().pendingItemsSaved()
                                .replace("{amount}", String.valueOf(pendingAmount))
                                .replace("{command}", item.currency() == ru.moscow.foxkiss.auction.AuctionCurrency.VAULT ? "/ah items" : "/dah items"), configManager));
                    }
                });
            });
        } else {
            scheduler.runAsync(() -> repository.updateJournalStatus(operationId, "COMPLETED"));
        }

        itemFactory.invalidateLotCache(item.id());

        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(item.sellerName());
        boolean deposited = transactionService.depositMoney(sellerOffline, item.currency(), totalPrice);
        if (!deposited) {
            scheduler.runAsync(() -> repository.savePendingPayout(item.sellerName(), item.currency(), totalPrice));
            plugin.getLogger().warning("Не удалось выплатить средства продавцу " + item.sellerName() + ", выплата сохранена в ожидающие.");
        }

        int totalItems = 0;
        if (contents != null) {
            for (ItemStack content : contents) {
                if (content != null && content.getType() != Material.AIR) {
                    totalItems += content.getAmount();
                }
            }
        }

        String str = PriceFormatter.format(totalPrice, item.currency());
        String ys = item.currency().symbol(configManager.getConfigValues());

        if (buyer.isOnline()) {
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().yspex()
                    .replace("{item_name}", item.sellerName())
                    .replace("{symbol_value}", str + ys), configManager));
        }

        Player seller = Bukkit.getPlayer(item.sellerName());
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

    private int countStacks(List<ItemStack> items) {
        int amount = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                amount++;
            }
        }
        return amount;
    }

    private void processBuy(Player buyer, AuctionItem item, int amount, long lotId, String operationId, Consumer<Boolean> callback) {
        if (!buyer.isOnline()) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            if (callback != null) callback.accept(false);
            return;
        }

        if (validationService.isOwner(buyer, item)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().noOwn(), callback);
            return;
        }

        if (validationService.isExpired(item)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback);
            return;
        }

        int buyAmount = Math.max(1, Math.min(amount, item.amount()));
        ItemStack b = item.itemStack().clone();
        b.setAmount(buyAmount);

        if (!validationService.canFit(buyer, b)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().inventoryFull(), callback);
            return;
        }

        double totalPrice = item.calculatePurchasePrice(buyAmount);
        if (!validationService.hasEnoughMoney(buyer, item.currency(), totalPrice)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().nomoney(), callback);
            return;
        }

        if (!transactionService.withdrawMoney(buyer, item.currency(), totalPrice)) {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            finishWithMessage(buyer, configManager.getConfigValues().messages().nomoney(), callback);
            return;
        }

        scheduler.runAsync(() -> {
            repository.updateJournalStatus(operationId, "MONEY_WITHDRAWN");
            PurchaseResult result = repository.processPurchase(lotId, operationId, buyer.getName(), buyAmount, totalPrice);
            scheduler.runSync(() -> {
                if (!result.success()) {
                    boolean refunded = transactionService.depositMoney(buyer, item.currency(), totalPrice);
                    if (!refunded) {
                        repository.savePendingPayout(buyer.getName(), item.currency(), totalPrice);
                        plugin.getLogger().warning("Не удалось вернуть средства покупателю " + buyer.getName() + ", возврат сохранён в ожидающие выплаты.");
                    }
                    repository.cancelProcessing(lotId, operationId);
                    repository.updateJournalStatus(operationId, "REFUNDED");
                    finishWithMessage(buyer, configManager.getConfigValues().messages().noId(), callback);
                    return;
                }
                completePurchase(buyer, item, result.purchasedItem(), buyAmount, totalPrice, result.pendingDeliveryId(), operationId, callback);
            });
        });
    }

    private void completePurchase(Player buyer, AuctionItem item, ItemStack purchasedItem, int buyAmount, double totalPrice, long deliveryId, String operationId, Consumer<Boolean> callback) {
        if (buyer.isOnline()) {
            transactionService.giveItem(buyer, purchasedItem);
            scheduler.runAsync(() -> repository.confirmDelivery(deliveryId, operationId));
        } else {
            scheduler.runAsync(() -> repository.updateJournalStatus(operationId, "COMPLETED"));
        }

        itemFactory.invalidateLotCache(item.id());

        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(item.sellerName());
        boolean deposited = transactionService.depositMoney(sellerOffline, item.currency(), totalPrice);
        if (!deposited) {
            scheduler.runAsync(() -> repository.savePendingPayout(item.sellerName(), item.currency(), totalPrice));
            plugin.getLogger().warning("Не удалось выплатить средства продавцу " + item.sellerName() + ", выплата сохранена в ожидающие.");
        }

        String i = ItemUtils.getItemDisplayName(purchasedItem);
        String str = PriceFormatter.format(totalPrice, item.currency());
        String ys = item.currency().symbol(configManager.getConfigValues());

        if (buyer.isOnline()) {
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().yspex().replace("{item_name}", i).replace("{symbol_value}", str + ys), configManager));
        }

        Player seller = Bukkit.getPlayer(item.sellerName());
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