package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

public final class AuctionSellService extends BaseAuctionService {

    private final LimitService limitService;
    private final AuctionValidationService validationService;

    public AuctionSellService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, LimitService limitService, AuctionValidationService validationService, AuctionTransactionService transactionService) {
        super(scheduler, configManager, repository, transactionService);
        this.limitService = limitService;
        this.validationService = validationService;
    }

    public void sell(Player player, AuctionCurrency currency, double price) {
        if (!validationService.isEconomyAvailable(currency)) {
            sendMessage(player, configManager.getConfigValues().messages().economyUnavailable());
            return;
        }

        if (!validationService.isValidPrice(price)) {
            sendMessage(player, configManager.getConfigValues().messages().noPrice());
            return;
        }

        boolean isDonate = currency == AuctionCurrency.PLAYER_POINTS;
        if (!validationService.isPriceInRange(price, currency, isDonate)) {
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

        scheduler.runAsync(() -> {
            int active = repository.countActiveBySellerSince(playerName, currency, 0);
            if (active >= limit) {
                scheduler.runSync(() -> sendMessage(player, configManager.getConfigValues().messages().limitReached()));
                return;
            }

            long id = repository.create(playerName, currency, soldItem, price);
            if (id <= 0) {
                scheduler.runSync(() -> sendMessage(player, configManager.getConfigValues().messages().databaseError()));
                return;
            }

            scheduler.runSync(() -> {
                transactionService.removeItemFromHand(player);
                String symbol = currency.symbol(configManager.getConfigValues());
                String formatted = PriceFormatter.format(price) + " " + symbol;
                String msg = configManager.getConfigValues().messages().sellSuccess().replace("{symbol_value}", formatted);
                player.sendMessage(PlaceholderUtils.applypapi(player, msg, configManager));
            });
        });
    }
}