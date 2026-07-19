package ru.moscow.foxkiss.auction;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.TextUtils;
import ru.moscow.foxkiss.utils.managers.interfaces.IMessageManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AuctionService {

    private final JavaPlugin plugin;
    private final IConfigManager configManager;
    private final IMessageManager messageManager;
    private final AuctionRepository repository;
    private final EconomyProvider economyProvider;
    private final LimitService limitService;

    private final HashMap<Object, Object> locks = new HashMap<>();

    public AuctionService(JavaPlugin plugin, IConfigManager configManager, IMessageManager messageManager,
                          AuctionRepository repository, EconomyProvider economyProvider, LimitService limitService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.repository = repository;
        this.economyProvider = economyProvider;
        this.limitService = limitService;
    }

    public CompletableFuture<Boolean> sell(Player player, AuctionCurrency currency, double price) {
        if (!economyProvider.available(currency)) {
            player.sendMessage(messageManager.getMessage(player, "economy-unavailable"));
            return CompletableFuture.completedFuture(false);
        }
        if (price <= 0) {
            player.sendMessage(messageManager.getMessage(player, "non-price"));
            return CompletableFuture.completedFuture(false);
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ru.moscow.foxkiss.utils.ItemUtils.isSellable(hand)) {
            player.sendMessage(messageManager.getMessage(player, "air"));
            return CompletableFuture.completedFuture(false);
        }

        ItemStack soldItem = hand.clone();

        Object lock = locks.computeIfAbsent(player.getUniqueId(), k -> new Object());
        synchronized (lock) {
            int maxDays = configManager.getConfigValues().maxAuctionStorageDays();
            int limit = limitService.getLimit(player, currency);

            return repository.createIfAllowed(
                    player.getName(),
                    currency,
                    soldItem,
                    price,
                    maxDays,
                    limit
            ).thenApply(id -> {
                if (id > 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.getInventory().setItemInMainHand(null);
                        String symbol = currency.symbol(configManager.getConfigValues());
                        String message = messageManager.getMessage(player, "commands-sell-success",
                                Map.of("symbol_value", PriceFormatter.format(price) + " " + symbol));
                        player.sendMessage(message);
                    });
                    return true;
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(messageManager.getMessage(player, "limit-reached"));
                    });
                    return false;
                }
            });
        }
    }

    public void buy(Player buyer, long lotId, int amount) {
        repository.markAsSelling(lotId).thenAccept(acquired -> {
            if (!acquired) {
                sendError(buyer, "no-id");
                return;
            }

            repository.findById(lotId).thenAccept(optItem -> {
                if (optItem.isEmpty()) {
                    restoreAndSendError(buyer, lotId, "no-id");
                    return;
                }

                AuctionItem item = optItem.get();

                if (item.sellerName().equals(buyer.getName())) {
                    restoreAndSendError(buyer, lotId, "no-own");
                    return;
                }

                if (item.expired(configManager.getConfigValues().maxAuctionStorageDays())) {
                    restoreAndSendError(buyer, lotId, "no-id");
                    return;
                }

                int buyAmount = Math.max(1, Math.min(amount, item.amount()));
                double price = item.pricePerItem() * buyAmount;

                if (!economyProvider.has(buyer, item.currency(), price)) {
                    restoreAndSendError(buyer, lotId, "nomoney");
                    return;
                }

                if (!economyProvider.withdraw(buyer, item.currency(), price)) {
                    restoreAndSendError(buyer, lotId, "nomoney");
                    return;
                }

                repository.delete(lotId).thenAccept(deleted -> {
                    if (!deleted) {
                        economyProvider.deposit(buyer, item.currency(), price);
                        restoreAndSendError(buyer, lotId, "no-id");
                        return;
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        ItemStack bought = item.itemStackClone();
                        bought.setAmount(buyAmount);
                        buyer.getInventory().addItem(bought).values()
                                .forEach(left -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), left));

                        int leftAmount = item.amount() - buyAmount;
                        if (leftAmount > 0) {
                            ItemStack left = item.itemStackClone();
                            left.setAmount(leftAmount);
                            double leftPrice = item.pricePerItem() * leftAmount;
                            repository.create(item.sellerName(), item.currency(), left, leftPrice);
                        }

                        OfflinePlayer seller = Bukkit.getOfflinePlayer(item.sellerName());
                        economyProvider.deposit(seller, item.currency(), price);

                        repository.recordSale(
                                item.sellerName(),
                                buyer.getName(),
                                item.currency(),
                                bought.getType().name(),
                                buyAmount,
                                price
                        );

                        String itemDisplayName = bought.getType().name().toLowerCase().replace('_', ' ');
                        ItemMeta meta = bought.getItemMeta();
                        if (meta != null && meta.hasDisplayName()) {
                            Component displayName = meta.displayName();
                            if (displayName != null) {
                                itemDisplayName = TextUtils.plain(displayName);
                            }
                        }

                        String priceStr = PriceFormatter.format(price);
                        String symbol = item.currency().symbol(configManager.getConfigValues());

                        buyer.sendMessage(messageManager.getMessage(buyer, "buy-yspex",
                                Map.of("symbol_value", PriceFormatter.format(price) + " " + symbol)));

                        Player onlineSeller = Bukkit.getPlayer(item.sellerName());
                        if (onlineSeller != null) {
                            onlineSeller.sendMessage(messageManager.getMessage(onlineSeller, "buy-seller",
                                    Map.of(
                                            "buyer", buyer.getName(),
                                            "item_name", itemDisplayName,
                                            "amount", String.valueOf(buyAmount),
                                            "price", priceStr,
                                            "symbol_value", symbol
                                    )
                            ));
                        }
                    });
                });
            });
        });
    }

    public void take(Player player, long lotId) {
        repository.findById(lotId).thenAccept(optionalItem -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (optionalItem.isEmpty()) {
                player.sendMessage(messageManager.getMessage(player, "no-id"));
                return;
            }

            AuctionItem item = optionalItem.get();
            if (!item.sellerName().equals(player.getName())) {
                player.sendMessage(messageManager.getMessage(player, "no-own"));
                return;
            }

            repository.delete(item.id()).thenAccept(deleted -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!deleted) {
                    player.sendMessage(messageManager.getMessage(player, "no-id"));
                    return;
                }
                ItemStack returned = item.itemStackClone();
                player.getInventory().addItem(returned).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                player.sendMessage(messageManager.getMessage(player, item.expired(configManager.getConfigValues().maxAuctionStorageDays())
                        ? "expired-take"
                        : "selling-take"));
            }));
        }));
    }

    private void restoreAndSendError(Player player, long lotId, String messageKey) {
        repository.restoreStatus(lotId);
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(messageManager.getMessage(player, messageKey));
        });
    }

    private void sendError(Player player, String messageKey) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(messageManager.getMessage(player, messageKey));
        });
    }
}