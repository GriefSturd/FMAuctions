package ru.moscow.foxkiss.auction.services.base;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.AuctionTransactionService;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.function.Consumer;

@RequiredArgsConstructor
public abstract class BaseAuctionService {
    protected final SchedulerService scheduler;
    protected final IConfigManager configManager;
    protected final AuctionRepository repository;
    protected final AuctionTransactionService transactionService;

    protected ConfigValues config() {
        return configManager.getConfigValues();
    }

    protected void sendMessage(Player player, String message) {
        if (player == null || !player.isOnline() || message == null || message.isEmpty()) return;
        player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
    }

    protected void finishWithMessage(Player player, String message, boolean result, Consumer<Boolean> callback) {
        sendMessage(player, message);
        if (callback != null) callback.accept(result);
    }

    protected void restoreAndFinish(Player player, long lotId, String message, Consumer<Boolean> callback) {
        scheduler.runAsync(() -> {
            repository.restoreStatus(lotId);
            scheduler.runSync(() -> {
                if (player.isOnline()) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                }
                if (callback != null) callback.accept(false);
            });
        });
    }

    protected double computeCommission(ConfigValues.CommissionRule rule, double price) {
        if (rule == null) return 0;
        double amount = switch (rule.mode()) {
            case FIXED -> rule.fixed();
            case PERCENT -> price * rule.percent() / 100.0;
        };
        if (amount <= 0) return 0;
        return Math.max(amount, rule.min());
    }

    protected double chargeCommission(Player player, AuctionCurrency ignored, ConfigValues.CommissionRule rule, double price) {
        ConfigValues.CommissionConfig cfg = config().commission();
        double commission = computeCommission(rule, price);
        if (commission <= 0) return 0;
        AuctionCurrency currency = rule.currency();
        String symbol = currency.symbol(config());
        if (!transactionService.hasEnoughMoney(player, currency, commission)) {
            sendMessage(player, config().messages().commissionNotEnough().replace("{amount}", PriceFormatter.format(commission)).replace("{symbol_value}", symbol));
            return -1;
        }
        if (!transactionService.withdrawMoney(player, currency, commission)) {
            sendMessage(player, config().messages().commissionNotEnough().replace("{amount}", PriceFormatter.format(commission)).replace("{symbol_value}", symbol));
            return -1;
        }
        sendMessage(player, config().messages().commissionCharged().replace("{amount}", PriceFormatter.format(commission)).replace("{symbol_value}", symbol));
        return commission;
    }
}
