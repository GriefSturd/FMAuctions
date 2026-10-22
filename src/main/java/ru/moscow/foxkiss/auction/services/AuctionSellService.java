package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionSellService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.scheduler.SchedulerService;

import java.util.Map;

public final class AuctionSellService extends BaseAuctionSellService {

    private final LimitService limitService;
    private final AuctionValidationService validationService;

    public AuctionSellService(FMAuction plugin, SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, LimitService limitService, AuctionValidationService validationService, AuctionTransactionService transactionService) {
        super(plugin, scheduler, configManager, repository, transactionService);
        this.limitService = limitService;
        this.validationService = validationService;
    }

    public void sell(Player player, AuctionCurrency currency, double price) {
        if (validationService.isEconomyAvailable(currency)) {
            sendMessage(player, configManager.getConfigValues().messages().economyUnavailable());
            return;
        }

        if (validationService.isValidPrice(price, currency)) {
            sendMessage(player, configManager.getConfigValues().messages().noPrice());
            return;
        }

        boolean isDonate = currency == AuctionCurrency.PLAYER_POINTS;
        if (validationService.isPriceInRange(price, currency, isDonate)) {
            String symbol = currency.symbol(configManager.getConfigValues());
            double min = validationService.getMinPrice(currency, isDonate);
            double max = validationService.getMaxPrice(currency, isDonate);
            String msg;
            if (price < min) {
                msg = configManager.getConfigValues().messages().priceTooLow().replace("{min_price}", String.valueOf((long) min)).replace("{symbol_value}", symbol);
            } else {
                msg = configManager.getConfigValues().messages().priceTooHigh().replace("{max_price}", String.valueOf((long) max)).replace("{symbol_value}", symbol);
            }
            sendMessage(player, msg);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!validationService.isSellableItem(hand)) {
            sendMessage(player, configManager.getConfigValues().messages().air());
            return;
        }

        int limit = limitService.getLimit(player, currency);
        String playerName = player.getName();
        ItemStack soldItem = hand.clone();

        submitListing(player, currency, price, config().commission().sell(),
                Map.of(player.getInventory().getHeldItemSlot(), soldItem),
                () -> repository.countActiveBySellerSince(playerName, currency, 0) >= limit,
                () -> repository.createWithinLimit(playerName, currency, soldItem, price, limit));
    }
}
