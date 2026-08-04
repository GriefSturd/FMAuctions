package ru.moscow.foxkiss.auction;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.services.*;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.scheduler.SchedulerService;

import java.util.function.Consumer;

@RequiredArgsConstructor
public final class AuctionService {

    private final AuctionSellService sellService;
    private final AuctionBuyService buyService;
    private final AuctionTakeService takeService;

    public static AuctionService create(JavaPlugin plugin, IConfigManager configManager, AuctionRepository repository, EconomyProvider economyProvider, LimitService limitService, ItemDisplayFactory itemFactory) {
        SchedulerService scheduler = new SchedulerService(plugin);
        AuctionValidationService validationService = new AuctionValidationService(configManager, economyProvider);
        AuctionTransactionService transactionService = new AuctionTransactionService(scheduler, repository, economyProvider, itemFactory);

        AuctionSellService sellService = new AuctionSellService(scheduler, configManager, repository, limitService, validationService, transactionService);
        AuctionBuyService buyService = new AuctionBuyService(scheduler, configManager, repository, validationService, transactionService, itemFactory);
        AuctionTakeService takeService = new AuctionTakeService(scheduler, configManager, repository, validationService, transactionService, itemFactory);

        return new AuctionService(sellService, buyService, takeService);
    }

    public void sell(Player player, AuctionCurrency currency, double price) {
        sellService.sell(player, currency, price);
    }

    public void buy(Player buyer, long lotId, int amount, Consumer<Boolean> callback) {
        buyService.buy(buyer, lotId, amount, callback);
    }

    public void take(Player player, long lotId, Consumer<Boolean> callback) {
        takeService.take(player, lotId, callback);
    }
}