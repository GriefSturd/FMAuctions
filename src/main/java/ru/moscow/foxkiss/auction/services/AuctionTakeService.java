package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.function.Consumer;

public final class AuctionTakeService extends BaseAuctionService {

    private final AuctionValidationService validationService;
    private final ItemDisplayFactory itemFactory;

    public AuctionTakeService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, AuctionValidationService validationService, AuctionTransactionService transactionService, ItemDisplayFactory itemFactory) {
        super(scheduler, configManager, repository, transactionService);
        this.validationService = validationService;
        this.itemFactory = itemFactory;
    }

    public void take(Player player, long lotId, Consumer<Boolean> callback) {
        scheduler.runAsync(() -> {
            if (!repository.markAsSelling(lotId)) {
                scheduler.runSync(() -> finishWithMessage(player, configManager.getConfigValues().messages().noId(), false, callback));
                return;
            }

            AuctionItem item = repository.findById(lotId).orElse(null);
            if (item == null) {
                repository.restoreStatus(lotId);
                scheduler.runSync(() -> finishWithMessage(player, configManager.getConfigValues().messages().noId(), false, callback));
                return;
            }

            scheduler.runSync(() -> processTake(player, item, lotId, callback));
        });
    }

    private void processTake(Player player, AuctionItem item, long lotId, Consumer<Boolean> callback) {
        if (!player.isOnline()) {
            transactionService.restoreStatus(lotId);
            if (callback != null) callback.accept(false);
            return;
        }

        if (!validationService.isOwner(player, item)) {
            restoreAndFinish(player, lotId, configManager.getConfigValues().messages().noOwn(), callback);
            return;
        }

        ItemStack returned = item.getItemStack().clone();
        if (!validationService.canFit(player, returned)) {
            restoreAndFinish(player, lotId, configManager.getConfigValues().messages().inventoryFull(), callback);
            return;
        }

        scheduler.runAsync(() -> {
            boolean deleted = repository.delete(lotId);
            scheduler.runSync(() -> {
                if (!deleted) {
                    transactionService.restoreStatus(lotId);
                    finishWithMessage(player, configManager.getConfigValues().messages().noId(), false, callback);
                    return;
                }
                completeTake(player, item, returned, callback);
            });
        });
    }

    private void completeTake(Player player, AuctionItem item, ItemStack returned, Consumer<Boolean> callback) {
        if (!player.isOnline()) {
            if (callback != null) callback.accept(false);
            return;
        }

        transactionService.giveItem(player, returned);
        itemFactory.invalidateLotCache(item.getId());

        String key = validationService.isExpired(item) ? "takeExpired" : "takeSelling";
        String message = key.equals("takeExpired")
                ? configManager.getConfigValues().messages().takeExpired()
                : configManager.getConfigValues().messages().takeSelling();
        player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
        if (callback != null) callback.accept(true);
    }
}