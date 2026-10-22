package ru.moscow.foxkiss.auction.services.base;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.AuctionTransactionService;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.logging.Level;

public abstract class BaseAuctionSellService extends BaseAuctionService {

    private final FMAuction plugin;

    protected BaseAuctionSellService(FMAuction plugin, SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, AuctionTransactionService transactionService) {
        super(scheduler, configManager, repository, transactionService);
        this.plugin = plugin;
    }
    protected void submitListing(Player player, AuctionCurrency currency, double price, ConfigValues.CommissionRule rule, Map<Integer, ItemStack> reserved, BooleanSupplier limitReached, LongSupplier createLot) {
        if (!transactionService.beginListing(player)) return;

        scheduler.runAsync(() -> {
            boolean reached;
            try {
                reached = limitReached.getAsBoolean();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось проверить лимит аукциона", e);
                scheduler.runSync(() -> {
                    transactionService.endListing(player);
                    sendMessage(player, config().messages().databaseError());
                });
                return;
            }

            scheduler.runSync(() -> {
                if (reached) {
                    transactionService.endListing(player);
                    sendMessage(player, config().messages().limitReached());
                    return;
                }

                chargeAndSubmit(player, currency, price, rule, reserved, createLot);
            });
        });
    }

    private void chargeAndSubmit(Player player, AuctionCurrency currency, double price, ConfigValues.CommissionRule rule, Map<Integer, ItemStack> reserved, LongSupplier createLot) {
        double commission;
        try {
            for (int slot : reserved.keySet()) {
                player.getInventory().setItem(slot, null);
            }
            commission = chargeCommission(player, currency, rule, price);
        } catch (RuntimeException e) {
            try {
                restoreItems(player, reserved);
            } finally {
                transactionService.endListing(player);
            }
            throw e;
        }
        if (commission == -1) {
            try {
                restoreItems(player, reserved);
            } finally {
                transactionService.endListing(player);
            }
            return;
        }

        try {
            scheduler.runAsync(() -> {
                String failure = null;
                try {
                    long id = createLot.getAsLong();
                    if (id == AuctionRepository.richi_limit) {
                        failure = config().messages().limitReached();
                    } else if (id < 0) {
                        failure = config().messages().databaseError();
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Не удалось создать лот на аукционе", e);
                    failure = config().messages().databaseError();
                }
                String errorMessage = failure;
                scheduler.runSync(() -> {
                    try {
                        if (errorMessage != null) {
                            rollback(player, rule, commission, reserved);
                            sendMessage(player, errorMessage);
                        } else {
                            if (commission > 0) {
                                String commissionMessage = config().messages().commissionCharged()
                                        .replace("{amount}", PriceFormatter.format(commission))
                                        .replace("{symbol_value}", rule.currency().symbol(config()));
                                sendMessage(player, commissionMessage);
                            }
                            String formatted = PriceFormatter.format(price) + currency.symbol(config());
                            sendMessage(player, config().messages().sellSuccess().replace("{symbol_value}", formatted));
                        }
                    } finally {
                        transactionService.endListing(player);
                    }
                });
            });
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось запланировать выставление лота на аукцион", e);
            try {
                rollback(player, rule, commission, reserved);
                sendMessage(player, config().messages().databaseError());
            } finally {
                transactionService.endListing(player);
            }
        }
    }

    private void rollback(Player player, ConfigValues.CommissionRule rule, double commission, Map<Integer, ItemStack> reserved) {
        restoreItems(player, reserved);
        if (commission > 0) {
            boolean refunded = transactionService.depositMoney(player, rule.currency(), commission);
            if (!refunded) {
                repository.savePendingPayout(player.getName(), rule.currency(), commission);
                plugin.getLogger().log(Level.WARNING, "Не удалось вернуть комиссию игроку " + player.getName() + ", выплата сохранена в ожидающие.");
            }
        }
    }

    private void restoreItems(Player player, Map<Integer, ItemStack> reserved) {
        for (Map.Entry<Integer, ItemStack> entry : reserved.entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();
            ItemStack current = player.getInventory().getItem(slot);
            if (current == null || current.getType().isAir()) {
                player.getInventory().setItem(slot, item.clone());
            } else {
                transactionService.giveItem(player, item.clone());
            }
        }
    }
}