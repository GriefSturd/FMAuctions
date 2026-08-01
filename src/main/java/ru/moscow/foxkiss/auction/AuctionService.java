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
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.Optional;
import java.util.function.Consumer;

public final class AuctionService {
    private final JavaPlugin plugin;
    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final EconomyProvider economyProvider;
    private final LimitService limitService;

    public AuctionService(JavaPlugin plugin, IConfigManager configManager, AuctionRepository repository, EconomyProvider economyProvider, LimitService limitService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.economyProvider = economyProvider;
        this.limitService = limitService;
    }

    public void sell(Player player, AuctionCurrency currency, double price) {
        if (!economyProvider.available(currency)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().economyUnavailable(), configManager));
            return;
        }

        if (price <= 0) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noPrice(), configManager));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemUtils.isSellable(hand)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().air(), configManager));
            return;
        }

        int maxDays = configManager.getConfigValues().maxAuctionStorageDays();
        int limit = limitService.getLimit(player, currency);
        long cutoff = System.currentTimeMillis() - maxDays * 86_400_000L;
        ItemStack soldItem = hand.clone();
        String playerName = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int count = repository.countActiveBySellerSince(playerName, currency, cutoff);
            if (count >= limit) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().limitReached(), configManager));
                });
                return;
            }

            long id = repository.create(playerName, currency, soldItem, price);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (id <= 0) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().databaseError(), configManager));
                    return;
                }

                player.getInventory().setItemInMainHand(null);
                String symbol = currency.symbol(configManager.getConfigValues());
                String formatted = PriceFormatter.format(price) + " " + symbol;
                player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().sellSuccess().replace("{symbol_value}", formatted), configManager));
            });
        });
    }

    public void buy(Player buyer, long lotId, int amount, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!repository.markAsSelling(lotId)) {
                finish(buyer, callback, false, configManager.getConfigValues().messages().noId());
                return;
            }

            Optional<AuctionItem> optional = repository.findById(lotId);
            if (optional.isEmpty()) {
                repository.restoreStatus(lotId);
                finish(buyer, callback, false, configManager.getConfigValues().messages().noId());
                return;
            }

            AuctionItem item = optional.get();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!buyer.isOnline()) {
                    restoreAsync(lotId);
                    callback.accept(false);
                    return;
                }

                if (item.sellerName().equalsIgnoreCase(buyer.getName())) {
                    restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().noOwn());
                    return;
                }

                if (item.expired(configManager.getConfigValues().maxAuctionStorageDays())) {
                    restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().noId());
                    return;
                }

                int buyAmount = Math.max(1, Math.min(amount, item.amount()));
                ItemStack bought = item.itemStackClone();
                bought.setAmount(buyAmount);

                if (!canFit(buyer, bought)) {
                    restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().inventoryFull());
                    return;
                }

                double totalPrice = item.pricePerItem() * buyAmount;
                if (!economyProvider.has(buyer, item.currency(), totalPrice)) {
                    restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().nomoney());
                    return;
                }

                if (!economyProvider.withdraw(buyer, item.currency(), totalPrice)) {
                    restoreAndFinish(buyer, lotId, callback, false, configManager.getConfigValues().messages().nomoney());
                    return;
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean deleted = repository.delete(lotId);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!buyer.isOnline()) {
                            if (deleted) economyProvider.deposit(buyer, item.currency(), totalPrice);
                            callback.accept(false);
                            return;
                        }

                        if (!deleted) {
                            economyProvider.deposit(buyer, item.currency(), totalPrice);
                            repository.restoreStatus(lotId);
                            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().noId(), configManager));
                            callback.accept(false);
                            return;
                        }

                        finishPurchase(buyer, item, buyAmount, totalPrice, callback);
                    });
                });
            });
        });
    }

    private void finishPurchase(Player buyer, AuctionItem item, int buyAmount, double totalPrice, Consumer<Boolean> callback) {
        try {
            ItemStack bought = item.itemStackClone();
            bought.setAmount(buyAmount);
            buyer.getInventory().addItem(bought).values().forEach(left -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), left));
            int leftAmount = item.amount() - buyAmount;
            if (leftAmount > 0) {
                ItemStack left = item.itemStackClone();
                left.setAmount(leftAmount);
                double leftPrice = item.pricePerItem() * leftAmount;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.create(item.sellerName(), item.currency(), left, leftPrice));
            }

            OfflinePlayer seller = Bukkit.getOfflinePlayer(item.sellerName());
            economyProvider.deposit(seller, item.currency(), totalPrice);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.recordSale(item.sellerName(), buyer.getName(), item.currency(), bought.getType().name(), buyAmount, totalPrice));
            String itemDisplayName = ItemUtils.getItemDisplayName(bought);
            String priceStr = PriceFormatter.format(totalPrice);
            String symbol = item.currency().symbol(configManager.getConfigValues());
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().yspex().replace("{symbol_value}", priceStr + " " + symbol), configManager));
            Player onlineSeller = Bukkit.getPlayer(item.sellerName());
            if (onlineSeller != null) {
                onlineSeller.sendMessage(PlaceholderUtils.applypapi(onlineSeller,
                        configManager.getConfigValues().messages().buySeller()
                                .replace("{buyer}", buyer.getName())
                                .replace("{item_name}", itemDisplayName)
                                .replace("{amount}", String.valueOf(buyAmount))
                                .replace("{price}", priceStr)
                                .replace("{symbol_value}", symbol),
                        configManager));
            }
            callback.accept(true);

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при завершении покупки: " + e.getMessage());
            economyProvider.deposit(buyer, item.currency(), totalPrice);
            restoreAsync(item.id());
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().databaseError(), configManager));
            callback.accept(false);
        }
    }

    public void take(Player player, long lotId, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!repository.markAsSelling(lotId)) {
                finish(player, callback, false, configManager.getConfigValues().messages().noId());
                return;
            }

            Optional<AuctionItem> optional = repository.findById(lotId);
            if (optional.isEmpty()) {
                repository.restoreStatus(lotId);
                finish(player, callback, false, configManager.getConfigValues().messages().noId());
                return;
            }

            AuctionItem item = optional.get();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    restoreAsync(lotId);
                    callback.accept(false);
                    return;
                }

                if (!item.sellerName().equalsIgnoreCase(player.getName())) {
                    restoreAndFinish(player, lotId, callback, false, configManager.getConfigValues().messages().noOwn());
                    return;
                }

                ItemStack returned = item.itemStackClone();
                if (!canFit(player, returned)) {
                    restoreAndFinish(player, lotId, callback, false, configManager.getConfigValues().messages().inventoryFull());
                    return;
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean deleted = repository.delete(lotId);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            callback.accept(false);
                            return;
                        }
                        if (!deleted) {
                            repository.restoreStatus(lotId);
                            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
                            callback.accept(false);
                            return;
                        }
                        player.getInventory().addItem(returned).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                        String message = item.expired(configManager.getConfigValues().maxAuctionStorageDays())
                                ? configManager.getConfigValues().messages().takeExpired()
                                : configManager.getConfigValues().messages().takeSelling();
                        player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                        callback.accept(true);
                    });
                });
            });
        });
    }

    private void finish(Player player, Consumer<Boolean> callback, boolean result, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
            callback.accept(result);
        });
    }

    private void restoreAndFinish(Player player, long lotId, Consumer<Boolean> callback, boolean result, String message) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            repository.restoreStatus(lotId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                callback.accept(result);
            });
        });
    }

    private void restoreAsync(long lotId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.restoreStatus(lotId));
    }

    private boolean canFit(Player player, ItemStack item) {
        Inventory inv = player.getInventory();
        int needed = item.getAmount();
        ItemStack template = item.clone();
        int emptySlots = 0;

        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                emptySlots++;
                continue;
            }
            if (slot.isSimilar(template)) {
                int free = slot.getMaxStackSize() - slot.getAmount();
                if (free > 0) {
                    int take = Math.min(free, needed);
                    needed -= take;
                    if (needed <= 0) return true;
                }
            }
        }

        int maxStack = template.getMaxStackSize();
        int slotsNeeded = (needed + maxStack - 1) / maxStack;
        return emptySlots >= slotsNeeded;
    }
}