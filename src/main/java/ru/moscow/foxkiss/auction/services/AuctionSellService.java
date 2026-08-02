package ru.moscow.foxkiss.auction.services;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

public final class AuctionSellService {

    private final SchedulerService scheduler;
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final LimitService limitService;
    private final AuctionValidationService validationService;
    private final AuctionTransactionService transactionService;

    public AuctionSellService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, LimitService limitService, AuctionValidationService validationService, AuctionTransactionService transactionService) {
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.repository = repository;
        this.limitService = limitService;
        this.validationService = validationService;
        this.transactionService = transactionService;
    }

    public void sell(Player player, AuctionCurrency currency, double price) {
        if (!validationService.isEconomyAvailable(currency)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().economyUnavailable(), configManager));
            return;
        }

        if (!validationService.isValidPrice(price)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noPrice(), configManager));
            return;
        }

        boolean isDonateAuction = currency == AuctionCurrency.PLAYER_POINTS;

        if (!validationService.isPriceInRange(price, currency, isDonateAuction)) {
            String symbol = currency.symbol(configManager.getConfigValues());
            double minPrice = validationService.getMinPrice(currency, isDonateAuction);
            double maxPrice = validationService.getMaxPrice(currency, isDonateAuction);
            
            if (price < minPrice) {
                String message = configManager.getConfigValues().messages().priceTooLow()
                        .replace("{min_price}", String.valueOf((long)minPrice))
                        .replace("{symbol_value}", symbol);
                player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                return;
            }
            
            if (price > maxPrice) {
                String message = configManager.getConfigValues().messages().priceTooHigh()
                        .replace("{max_price}", String.valueOf((long)maxPrice))
                        .replace("{symbol_value}", symbol);
                player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                return;
            }
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!validationService.isSellableItem(hand)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().air(), configManager));
            return;
        }

        int limit = limitService.getLimit(player, currency);
        ItemStack soldItem = hand.clone();
        String playerName = player.getName();

        scheduler.runAsyncThenSync(
            () -> {
                int count = repository.countActiveBySellerSince(playerName, currency, 0);
                
                if (count >= limit) {
                    return new SellResult(false, 0, "limitReached");
                }
                
                long id = repository.create(playerName, currency, soldItem, price);
                if (id <= 0) {
                    return new SellResult(false, 0, "databaseError");
                }
                
                return new SellResult(true, id, null);
            },
            result -> {
                if (!player.isOnline()) return;
                
                if (!result.success) {
                    String message = result.errorKey.equals("limitReached")
                        ? configManager.getConfigValues().messages().limitReached()
                        : configManager.getConfigValues().messages().databaseError();
                    player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                    return;
                }

                transactionService.removeItemFromHand(player);
                String symbol = currency.symbol(configManager.getConfigValues());
                String formatted = PriceFormatter.format(price) + " " + symbol;
                player.sendMessage(PlaceholderUtils.applypapi(player,
                        configManager.getConfigValues().messages().sellSuccess()
                                .replace("{symbol_value}", formatted), configManager));
            }
        );
    }

    private record SellResult(boolean success, long lotId, String errorKey) {}
}
