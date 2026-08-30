package ru.moscow.foxkiss.auction;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.services.AuctionBuyService;
import ru.moscow.foxkiss.auction.services.AuctionSellInventoryService;
import ru.moscow.foxkiss.auction.services.AuctionSellService;
import ru.moscow.foxkiss.auction.services.AuctionTakeService;
import ru.moscow.foxkiss.auction.services.AuctionTransactionService;
import ru.moscow.foxkiss.auction.services.AuctionValidationService;

import java.util.function.Consumer;

@RequiredArgsConstructor
public final class AuctionService {

    private final AuctionSellService sellService;
    private final AuctionSellInventoryService sellInventoryService;
    private final AuctionBuyService buyService;
    private final AuctionTakeService takeService;

    public static AuctionService create(ru.moscow.foxkiss.config.interfaces.IConfigManager configManager, AuctionRepository repository, ru.moscow.foxkiss.economy.EconomyProvider economyProvider, ru.moscow.foxkiss.permissions.LimitService limitService, ru.moscow.foxkiss.gui.ItemDisplayFactory itemFactory, ru.moscow.foxkiss.scheduler.SchedulerService scheduler) {
        AuctionValidationService validationService = new AuctionValidationService(configManager, economyProvider);
        AuctionTransactionService transactionService = new AuctionTransactionService(scheduler, repository, economyProvider, itemFactory);

        AuctionSellService sellService = new AuctionSellService(scheduler, configManager, repository, limitService, validationService, transactionService);
        AuctionSellInventoryService sellInventoryService = new AuctionSellInventoryService(scheduler, configManager, repository, limitService, validationService, transactionService);
        AuctionBuyService buyService = new AuctionBuyService(scheduler, configManager, repository, validationService, transactionService, itemFactory);
        AuctionTakeService takeService = new AuctionTakeService(scheduler, configManager, repository, validationService, transactionService, itemFactory);

        return new AuctionService(sellService, sellInventoryService, buyService, takeService);
    }

    public void sell(Player player, AuctionCurrency currency, double price) {
        sellService.sell(player, currency, price);
    }

    public void sellInventory(Player player, AuctionCurrency currency, double price) {
        sellInventoryService.sellInventory(player, currency, price);
    }

    public void buy(Player buyer, long lotId, int amount, Consumer<Boolean> callback) {
        buyService.buy(buyer, lotId, amount, callback);
    }

    public void buyInventory(Player buyer, long lotId, Consumer<Boolean> callback) {
        buyService.buyInventory(buyer, lotId, callback);
    }

    public void take(Player player, long lotId, Consumer<Boolean> callback) {
        takeService.take(player, lotId, callback);
    }
}
