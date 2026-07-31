package ru.moscow.foxkiss.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.managers.interfaces.IMessageManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class AuctionService {

    private final JavaPlugin plugin;
    private final IConfigManager configManager;
    private final IMessageManager messageManager;
    private final AuctionRepository repository;
    private final EconomyProvider economyProvider;
    private final LimitService limitService;

    private final Map<UUID, Object> locks = new HashMap<>();

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
            sendMessage(player, "economy-unavailable");
            return completedFalse();
        }
        if (price <= 0) {
            sendMessage(player, "non-price");
            return completedFalse();
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemUtils.isSellable(hand)) {
            sendMessage(player, "air");
            return completedFalse();
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
                    runSync(() -> {
                        player.getInventory().setItemInMainHand(null);
                        String symbol = currency.symbol(configManager.getConfigValues());
                        String formatted = PriceFormatter.format(price) + " " + symbol;
                        sendMessage(player, "commands-sell-success",
                                Map.of("symbol_value", formatted));
                    });
                    return true;
                } else if (id == -1L) {
                    runSync(() -> sendMessage(player, "limit-reached"));
                    return false;
                } else {
                    runSync(() -> sendMessage(player, "database-error"));
                    return false;
                }
            });
        }
    }
    
    public CompletableFuture<Boolean> buy(Player buyer, long lotId, int amount) {
        return repository.markAsSelling(lotId)
                .thenCompose(acquired -> {
                    if (!acquired) {
                        sendMessage(buyer, "no-id");
                        return CompletableFuture.completedFuture(false);
                    }
                    return repository.findById(lotId)
                            .thenCompose(optItem -> processBuy(buyer, optItem, lotId, amount));
                })
                .exceptionally(error -> {
                    repository.restoreStatus(lotId);
                    runSync(() -> sendMessage(buyer, "no-id"));
                    return false;
                });
    }

    private CompletableFuture<Boolean> processBuy(Player buyer, Optional<AuctionItem> optItem, long lotId, int amount) {
        if (optItem.isEmpty()) {
            return restoreAndFail(buyer, lotId, "no-id").thenApply(ignored -> false);
        }

        AuctionItem item = optItem.get();

        if (item.sellerName().equals(buyer.getName())) {
            return restoreAndFail(buyer, lotId, "no-own").thenApply(ignored -> false);
        }

        if (item.expired(configManager.getConfigValues().maxAuctionStorageDays())) {
            return restoreAndFail(buyer, lotId, "no-id").thenApply(ignored -> false);
        }

        int buyAmount = Math.max(1, Math.min(amount, item.amount()));
        ItemStack bought = item.itemStackClone();
        bought.setAmount(buyAmount);

        return canFitAsync(buyer, bought).thenCompose(canFit -> {
            if (!canFit) {
                return restoreAndFail(buyer, lotId, "inventory-full").thenApply(ignored -> false);
            }

            double totalPrice = item.pricePerItem() * buyAmount;

            if (!economyProvider.has(buyer, item.currency(), totalPrice)) {
                return restoreAndFail(buyer, lotId, "nomoney").thenApply(ignored -> false);
            }

            if (!economyProvider.withdraw(buyer, item.currency(), totalPrice)) {
                return restoreAndFail(buyer, lotId, "nomoney").thenApply(ignored -> false);
            }

            return repository.delete(lotId)
                    .thenCompose(deleted -> {
                        if (!deleted) {
                            economyProvider.deposit(buyer, item.currency(), totalPrice);
                            return restoreAndFail(buyer, lotId, "no-id").thenApply(ignored -> false);
                        }
                        return finalizePurchase(buyer, item, buyAmount, totalPrice)
                                .thenApply(ignored -> true);
                    });
        });
    }

    private CompletableFuture<Void> finalizePurchase(Player buyer, AuctionItem item, int buyAmount, double totalPrice) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runSync(() -> {
            try {
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
                economyProvider.deposit(seller, item.currency(), totalPrice);

                repository.recordSale(
                        item.sellerName(),
                        buyer.getName(),
                        item.currency(),
                        bought.getType().name(),
                        buyAmount,
                        totalPrice
                );

                String itemDisplayName = ItemUtils.getItemDisplayName(bought);
                String priceStr = PriceFormatter.format(totalPrice);
                String symbol = item.currency().symbol(configManager.getConfigValues());
                sendMessage(buyer, "buy-yspex",
                        Map.of("symbol_value", priceStr + " " + symbol));

                Player onlineSeller = Bukkit.getPlayer(item.sellerName());
                if (onlineSeller != null) {
                    sendMessage(onlineSeller, "buy-seller", Map.of(
                            "buyer", buyer.getName(),
                            "item_name", itemDisplayName,
                            "amount", String.valueOf(buyAmount),
                            "price", priceStr,
                            "symbol_value", symbol
                    ));
                }
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> take(Player player, long lotId) {
        return repository.markAsSelling(lotId).thenCompose(acquired -> {
            if (!acquired) {
                runSync(() -> sendMessage(player, "no-id"));
                return completedFalse();
            }
            return repository.findById(lotId).thenCompose(optItem -> {
                if (optItem.isEmpty()) return restoreAndFail(player, lotId, "no-id").thenApply(ignored -> false);

                AuctionItem item = optItem.get();
                if (!item.sellerName().equalsIgnoreCase(player.getName())) {
                    return restoreAndFail(player, lotId, "no-own").thenApply(ignored -> false);
                }

                ItemStack returned = item.itemStackClone();

                return canFitAsync(player, returned).thenCompose(canFit -> {
                    if (!canFit) return restoreAndFail(player, lotId, "inventory-full").thenApply(ignored -> false);

                    return repository.delete(lotId).thenCompose(deleted -> runSyncResult(() -> {
                        if (!deleted) {
                            sendMessage(player, "no-id");
                            return false;
                        }
                        player.getInventory().addItem(returned).values().forEach(left ->
                                player.getWorld().dropItemNaturally(player.getLocation(), left));

                        String key = item.expired(configManager.getConfigValues().maxAuctionStorageDays())
                                ? "expired-take" : "selling-take";
                        sendMessage(player, key);
                        return true;
                    }));
                });
            });
        }).exceptionally(error -> {
            repository.restoreStatus(lotId);
            runSync(() -> sendMessage(player, "no-id"));
            return false;
        });
    }

    private boolean canFit(Player player, ItemStack item) {
        Inventory inv = player.getInventory();
        int amount = item.getAmount();
        ItemStack copy = item.clone();

        for (ItemStack invItem : inv.getStorageContents()) {
            if (invItem == null || invItem.getType() == Material.AIR) continue;
            if (invItem.isSimilar(copy)) {
                int maxStack = invItem.getMaxStackSize();
                int space = maxStack - invItem.getAmount();
                if (space > 0) {
                    int toAdd = Math.min(space, amount);
                    amount -= toAdd;
                    if (amount == 0) return true;
                }
            }
        }

        if (amount > 0) {
            int emptySlots = 0;
            for (ItemStack invItem : inv.getStorageContents()) {
                if (invItem == null || invItem.getType() == Material.AIR) {
                    emptySlots++;
                }
            }
            int maxStack = copy.getMaxStackSize();
            int slotsNeeded = (int) Math.ceil((double) amount / maxStack);
            return emptySlots >= slotsNeeded;
        }
        return true;
    }

    private CompletableFuture<Boolean> canFitAsync(Player player, ItemStack item) {
        if (Bukkit.isPrimaryThread()) return CompletableFuture.completedFuture(canFit(player, item));
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        runSync(() -> result.complete(canFit(player, item)));
        return result;
    }

    private CompletableFuture<Void> restoreAndFail(Player player, long lotId, String key) {
        return repository.restoreStatus(lotId)
                .handle((ignored, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING,
                                "Ошибка при восстановлении лота аукциона " + lotId, error);
                    }
                    runSync(() -> sendMessage(player, key));
                    return null;
                });
    }

    private void sendMessage(Player player, String key) {
        player.sendMessage(messageManager.getMessage(player, key));
    }

    private void sendMessage(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(messageManager.getMessage(player, key, placeholders));
    }

    private void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private <T> CompletableFuture<T> runSyncResult(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        runSync(() -> {
            try {
                result.complete(task.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }

    private CompletableFuture<Boolean> completedFalse() {
        return CompletableFuture.completedFuture(false);
    }
}