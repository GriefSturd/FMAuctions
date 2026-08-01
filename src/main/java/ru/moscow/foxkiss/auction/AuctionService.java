package ru.moscow.foxkiss.auction;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.services.*;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.scheduler.SchedulerService;

import java.util.function.Consumer;

public final class AuctionService {
    
    private final AuctionSellService sellService;
    private final AuctionBuyService buyService;
    private final AuctionTakeService takeService;

    public AuctionService(JavaPlugin plugin, IConfigManager configManager, AuctionRepository repository, 
                          EconomyProvider economyProvider, LimitService limitService) {
        
        SchedulerService scheduler = new SchedulerService(plugin);
        AuctionValidationService validationService = new AuctionValidationService(configManager, economyProvider);
        AuctionTransactionService transactionService = new AuctionTransactionService(scheduler, repository, economyProvider);
        
        this.sellService = new AuctionSellService(scheduler, configManager, repository, limitService, 
                validationService, transactionService);
        this.buyService = new AuctionBuyService(scheduler, configManager, repository, 
                validationService, transactionService);
        this.takeService = new AuctionTakeService(scheduler, configManager, repository, 
                validationService, transactionService);
    }

    public void sell(Player player, AuctionCurrency currency, double price) {
        sellService.sell(player, currency, price);
    }

    public boolean buy(Player buyer, long lotId, int amount) {
        final boolean[] result = {false};
        buyService.buy(buyer, lotId, amount, success -> result[0] = success);
        return result[0];
    }

    public void buy(Player buyer, long lotId, int amount, Consumer<Boolean> callback) {
        buyService.buy(buyer, lotId, amount, callback);
    }

    public boolean take(Player player, long lotId) {
        final boolean[] result = {false};
        takeService.take(player, lotId, success -> result[0] = success);
        return result[0];
    }

    public void take(Player player, long lotId, Consumer<Boolean> callback) {
        takeService.take(player, lotId, callback);
    }
}
