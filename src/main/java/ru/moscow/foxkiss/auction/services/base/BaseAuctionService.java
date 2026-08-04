package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.function.Consumer;

public abstract class BaseAuctionService {
    protected final SchedulerService scheduler;
    protected final IConfigManager configManager;
    protected final AuctionRepository repository;
    protected final AuctionTransactionService transactionService;

    protected BaseAuctionService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, AuctionTransactionService transactionService) {
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.repository = repository;
        this.transactionService = transactionService;
    }

    protected ConfigValues config() {
        return configManager.getConfigValues();
    }

    protected void sendMessage(Player player, String message) {
        if (player == null || !player.isOnline() || message == null || message.isEmpty()) return;
        player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
    }

    protected void restoreStatusAsync(long lotId) {
        scheduler.runAsync(() -> repository.restoreStatus(lotId));
    }

    protected void finishWithMessage(Player player, String message, boolean result, Consumer<Boolean> callback) {
        scheduler.runSync(() -> {
            sendMessage(player, message);
            if (callback != null) callback.accept(result);
        });
    }

    protected void restoreAndFinish(Player player, long lotId, String message, Consumer<Boolean> callback) {
        scheduler.runAsync(() -> {
            repository.restoreStatus(lotId);
            scheduler.runSync(() -> {
                sendMessage(player, message);
                if (callback != null) callback.accept(false);
            });
        });
    }
}