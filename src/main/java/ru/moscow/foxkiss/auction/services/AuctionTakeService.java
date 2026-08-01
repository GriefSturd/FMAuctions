package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.Optional;
import java.util.function.Consumer;

public final class AuctionTakeService {

    private final SchedulerService scheduler;
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final AuctionValidationService validationService;
    private final AuctionTransactionService transactionService;

    public AuctionTakeService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, AuctionValidationService validationService, AuctionTransactionService transactionService) {
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.repository = repository;
        this.validationService = validationService;
        this.transactionService = transactionService;
    }

    public void take(Player player, long lotId, Consumer<Boolean> callback) {
        scheduler.runAsyncThenSync(
            () -> {
                if (!repository.markAsSelling(lotId)) {
                    return Optional.<AuctionItem>empty();
                }
                Optional<AuctionItem> item = repository.findById(lotId);
                if (item.isEmpty()) {
                    repository.restoreStatus(lotId);
                }
                return item;
            },
            optional -> {
                if (optional.isEmpty()) {
                    finish(player, callback, false, configManager.getConfigValues().messages().noId());
                    return;
                }
                processTake(player, optional.get(), lotId, callback);
            }
        );
    }

    private void processTake(Player player, AuctionItem item, long lotId, Consumer<Boolean> callback) {
        if (!player.isOnline()) {
            transactionService.restoreStatusAsync(lotId);
            callback.accept(false);
            return;
        }

        if (!validationService.isOwner(player, item)) {
            restoreAndFinish(player, lotId, callback, false, configManager.getConfigValues().messages().noOwn());
            return;
        }

        ItemStack returned = item.itemStackClone();
        if (!validationService.canFit(player, returned)) {
            restoreAndFinish(player, lotId, callback, false, configManager.getConfigValues().messages().inventoryFull());
            return;
        }

        scheduler.runAsyncThenSync(
            () -> repository.delete(lotId),
            deleted -> completeTake(player, item, returned, deleted, callback)
        );
    }

    private void completeTake(Player player, AuctionItem item, ItemStack returned, boolean deleted, Consumer<Boolean> callback) {
        if (!player.isOnline()) {
            callback.accept(false);
            return;
        }
        
        if (!deleted) {
            repository.restoreStatus(item.id());
            player.sendMessage(PlaceholderUtils.applypapi(player,
                    configManager.getConfigValues().messages().noId(), configManager));
            callback.accept(false);
            return;
        }
        
        transactionService.giveItem(player, returned);
        
        String message = validationService.isExpired(item)
                ? configManager.getConfigValues().messages().takeExpired()
                : configManager.getConfigValues().messages().takeSelling();
        
        player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
        callback.accept(true);
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
