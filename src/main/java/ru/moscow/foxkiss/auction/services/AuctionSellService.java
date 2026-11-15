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
import ru.moscow.foxkiss.utils.ItemUtils;

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
        if (!validateListingPrice(player, currency, price, validationService)) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!ItemUtils.isSellable(hand)) {
            sendMessage(player, config().messages().air());
            return;
        }

        int limit = limitService.getLimit(player, currency);
        long cutoff = System.currentTimeMillis() - config().maxAuctionStorageDays() * 86_400_000L;
        String playerName = player.getName();
        ItemStack soldItem = hand.clone();

        submitListing(
                player,
                currency,
                price,
                config().commission().sell(),
                Map.of(player.getInventory().getHeldItemSlot(), soldItem),
                () -> repository.countActiveBySellerSince(
                        playerName, currency, cutoff
                ) >= limit,
                () -> repository.createWithinLimit(
                        playerName, currency, soldItem, price, limit, cutoff
                )
        );
    }
}
