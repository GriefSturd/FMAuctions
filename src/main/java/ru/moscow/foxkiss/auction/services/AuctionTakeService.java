package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionLotType;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.TakeResult;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionService;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.UUID;
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
        String operationId = UUID.randomUUID().toString();
        String playerName = player.getName();
        long now = System.currentTimeMillis();
        scheduler.runAsync(() -> {
            if (repository.markAsProcessing(lotId, operationId, now)) {
                scheduler.runSync(() -> finishWithMessage(player, configManager.getConfigValues().messages().noId(), callback));
                return;
            }

            AuctionItem item = repository.findById(lotId).orElse(null);
            if (item == null) {
                repository.cancelProcessing(lotId, operationId);
                scheduler.runSync(() -> finishWithMessage(player, configManager.getConfigValues().messages().noId(), callback));
                return;
            }

            repository.logTransaction(operationId, lotId, "TAKE", playerName, item.sellerName(), item.currency(), item.amount(), 0.0, "STARTED");
            scheduler.runSync(() -> processTake(player, item, lotId, operationId, callback));
        });
    }

    private void processTake(Player player, AuctionItem item, long lotId, String operationId, Consumer<Boolean> callback) {
        if (!player.isOnline()) {
            cancelTake(lotId, operationId, () -> { if (callback != null) callback.accept(false); });
            return;
        }

        if (!validationService.isOwner(player, item)) {
            cancelTake(lotId, operationId, () -> finishWithMessage(player, configManager.getConfigValues().messages().noOwn(), callback));
            return;
        }

        boolean fits = item.type() == AuctionLotType.INVENTORY ? AuctionValidationService.canFitAll(player, item.inventoryContents()) : validationService.canFit(player, item.itemStack());
        if (!fits) {
            cancelTake(lotId, operationId, () -> finishWithMessage(player, configManager.getConfigValues().messages().inventoryFull(), callback));
            return;
        }

        String playerName = player.getName();
        scheduler.runAsync(() -> {
            TakeResult result = repository.processTake(lotId, operationId, playerName);
            if (!result.success()) {
                repository.cancelProcessing(lotId, operationId);
                repository.updateJournalStatus(operationId, "CANCELLED");
            }
            scheduler.runSync(() -> {
                if (!result.success()) {
                    finishWithMessage(player, configManager.getConfigValues().messages().noId(), callback);
                    return;
                }
                completeTake(player, result, callback);
            });
        });
    }

    private void cancelTake(long lotId, String operationId, Runnable callback) {
        scheduler.runAsync(() -> {
            repository.cancelProcessing(lotId, operationId);
            repository.updateJournalStatus(operationId, "CANCELLED");
            scheduler.runSync(callback);
        });
    }

    private void completeTake(Player player, TakeResult result, Consumer<Boolean> callback) {
        if (!player.isOnline()) {
            if (callback != null) callback.accept(false);
            return;
        }

        AuctionItem item = result.item();
        transactionService.deliverTakenLot(player, result, configManager);
        itemFactory.invalidateLotCache(item.id());

        ConfigValues.ConfigMessages messages = configManager.getConfigValues().messages();
        String message = validationService.isExpired(item) ? messages.takeExpired() : messages.takeSelling();
        player.sendMessage(TextUtils.component(PlaceholderUtils.apply(player, message, configManager)));
        if (callback != null) callback.accept(true);
    }
}
