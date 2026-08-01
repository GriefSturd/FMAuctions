package ru.moscow.foxkiss.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

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
    private final AuctionRepository repository;
    private final EconomyProvider economyProvider;
    private final LimitService limitService;

    private final Map<UUID, Object> locks = new HashMap<>();

    public AuctionService(JavaPlugin plugin, IConfigManager configManager,
                          AuctionRepository repository, EconomyProvider economyProvider, LimitService limitService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.economyProvider = economyProvider;
        this.limitService = limitService;
    }

    public CompletableFuture<Boolean> sell(Player player, AuctionCurrency currency, double price) {
        if (!economyProvider.available(currency)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, messages().economyUnavailable(), configManager));
            return completedFalse();
        }
        if (price <= 0) {
            player.sendMessage(PlaceholderUtils.applypapi(player, messages().noPrice(), configManager));
            return completedFalse();
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemUtils.isSellable(hand)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, messages().air(), configManager));
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
                        player.sendMessage(PlaceholderUtils.applypapi(player, messages().sellSuccess().replace("{symbol_value}", formatted), configManager));
                    });
                    return true;
                } else if (id == -1L) {
                    runSync(() -> player.sendMessage(PlaceholderUtils.applypapi(player, messages().limitReached(), configManager)));
                    return false;
                } else {
                    runSync(() -> player.sendMessage(PlaceholderUtils.applypapi(player, messages().databaseError(), configManager)));
                    return false;
                }
            });
        }
    }
    
    public CompletableFuture<Boolean> buy(Player buyer, long lotId, int amount) {
        return repository.markAsSelling(lotId)
                .thenCompose(acquired -> {
                    if (!acquired) {
                        buyer.sendMessage(PlaceholderUtils.applypapi(buyer, messages().noId(), configManager));
                        return CompletableFuture.completedFuture(false);
                    }
                    return repository.findById(lotId)
                            .thenCompose(optItem -> processBuy(buyer, optItem, lotId, amount));
                })
                .exceptionally(error -> {
                    repository.restoreStatus(lotId);
                    runSync(() -> buyer.sendMessage(PlaceholderUtils.applypapi(buyer, messages().noId(), configManager)));
                    return false;
                });
    }

    private CompletableFuture<Boolean> processBuy(Player buyer, Optional<AuctionItem> optItem, long lotId, int amount) {
        if (optItem.isEmpty()) {
            return restoreAndFail(buyer, lotId, messages().noId()).thenApply(ignored -> false);
        }

        AuctionItem item = optItem.get();

        if (item.sellerName().equals(buyer.getName())) {
            return restoreAndFail(buyer, lotId, messages().noOwn()).thenApply(ignored -> false);
        }

        if (item.expired(configManager.getConfigValues().maxAuctionStorageDays())) {
            return restoreAndFail(buyer, lotId, messages().noId()).thenApply(ignored -> false);
        }

        int buyAmount = Math.max(1, Math.min(amount, item.amount()));
        ItemStack bought = item.itemStackClone();
        bought.setAmount(buyAmount);

        return canFitAsync(buyer, bought).thenCompose(canFit -> {
            if (!canFit) {
                return restoreAndFail(buyer, lotId, messages().inventoryFull()).thenApply(ignored -> false);
            }

            double totalPrice = item.pricePerItem() * buyAmount;

            if (!economyProvider.has(buyer, item.currency(), totalPrice)) {
                return restoreAndFail(buyer, lotId, messages().nomoney()).thenApply(ignored -> false);
            }

            if (!economyProvider.withdraw(buyer, item.currency(), totalPrice)) {
                return restoreAndFail(buyer, lotId, messages().nomoney()).thenApply(ignored -> false);
            }

            return repository.delete(lotId)
                    .thenCompose(deleted -> {
                        if (!deleted) {
                            economyProvider.deposit(buyer, item.currency(), totalPrice);
                            return restoreAndFail(buyer, lotId, messages().noId()).thenApply(ignored -> false);
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
                buyer.sendMessage(PlaceholderUtils.applypapi(buyer, messages().yspex().replace("{symbol_value}", priceStr + " " + symbol), configManager));

                Player onlineSeller = Bukkit.getPlayer(item.sellerName());
                if (onlineSeller != null) {
                    onlineSeller.sendMessage(PlaceholderUtils.applypapi(onlineSeller, messages().buySeller()
                            .replace("{buyer}", buyer.getName())
                            .replace("{item_name}", itemDisplayName)
                            .replace("{amount}", String.valueOf(buyAmount))
                            .replace("{price}", priceStr)
                            .replace("{symbol_value}", symbol), configManager));
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
                runSync(() -> player.sendMessage(PlaceholderUtils.applypapi(player, messages().noId(), configManager)));
                return completedFalse();
            }
            return repository.findById(lotId).thenCompose(optItem -> {
                if (optItem.isEmpty()) return restoreAndFail(player, lotId, messages().noId()).thenApply(ignored -> false);

                AuctionItem item = optItem.get();
                if (!item.sellerName().equalsIgnoreCase(player.getName())) {
                    return restoreAndFail(player, lotId, messages().noOwn()).thenApply(ignored -> false);
                }

                ItemStack returned = item.itemStackClone();

                return canFitAsync(player, returned).thenCompose(canFit -> {
                    if (!canFit) return restoreAndFail(player, lotId, messages().inventoryFull()).thenApply(ignored -> false);

                    return repository.delete(lotId).thenCompose(deleted -> runSyncResult(() -> {
                        if (!deleted) {
                            player.sendMessage(PlaceholderUtils.applypapi(player, messages().noId(), configManager));
                            return false;
                        }
                        player.getInventory().addItem(returned).values().forEach(left ->
                                player.getWorld().dropItemNaturally(player.getLocation(), left));

                        player.sendMessage(PlaceholderUtils.applypapi(player,
                                item.expired(configManager.getConfigValues().maxAuctionStorageDays())
                                        ? messages().takeExpired() : messages().takeSelling(), configManager));
                        return true;
                    }));
                });
            });
        }).exceptionally(error -> {
            repository.restoreStatus(lotId);
            runSync(() -> player.sendMessage(PlaceholderUtils.applypapi(player, messages().noId(), configManager)));
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

    private CompletableFuture<Void> restoreAndFail(Player player, long lotId, String message) {
        return repository.restoreStatus(lotId)
                .handle((ignored, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING,
                                "Ошибка при восстановлении лота аукциона " + lotId, error);
                    }
                    runSync(() -> player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager)));
                    return null;
                });
    }

    private ConfigValues.ConfigMessages messages() {
        return configManager.getConfigValues().messages();
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
