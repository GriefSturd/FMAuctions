package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.scheduler.SchedulerService;

import java.util.function.Consumer;

public final class AuctionTakeService extends BaseAuctionService {
    private final AuctionValidationService validationService;

    public AuctionTakeService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, AuctionValidationService validationService, AuctionTransactionService transactionService) {
        super(scheduler, configManager, repository, transactionService);
        this.validationService = validationService;
    }

    public void take(Player player, long lotId, Consumer<Boolean> callback) {
        scheduler.runAsync(() -> {
            if (!repository.markAsSelling(lotId)) {
                finishWithMessage(player, config().messages().noId(), false, callback);
                return;
            }

            AuctionItem item = repository.findById(lotId).orElse(null);
            if (item == null) {
                repository.restoreStatus(lotId);
                finishWithMessage(player, config().messages().noId(), false, callback);
                return;
            }

            if (!player.isOnline()) {
                restoreStatusAsync(lotId);
                if (callback != null) callback.accept(false);
                return;
            }

            if (!validationService.isOwner(player, item)) {
                restoreAndFinish(player, lotId, config().messages().noOwn(), callback);
                return;
            }

            ItemStack returned = item.itemStackClone();
            if (!validationService.canFit(player, returned)) {
                restoreAndFinish(player, lotId, config().messages().inventoryFull(), callback);
                return;
            }

            boolean deleted = repository.delete(lotId);
            if (!deleted) {
                restoreStatusAsync(lotId);
                finishWithMessage(player, config().messages().noId(), false, callback);
                return;
            }

            scheduler.runSync(() -> {
                transactionService.giveItem(player, returned);
                String msg = validationService.isExpired(item)
                        ? config().messages().takeExpired()
                        : config().messages().takeSelling();
                sendMessage(player, msg);
                if (callback != null) callback.accept(true);
            });
        });
    }
}