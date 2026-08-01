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
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.*;

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

    public boolean sell(Player player, AuctionCurrency currency, double price) {
        if (!economyProvider.available(currency)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().economyUnavailable(), configManager));
            return false;
        }
        if (price <= 0) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noPrice(), configManager));
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemUtils.isSellable(hand)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().air(), configManager));
            return false;
        }

        int maxDays = configManager.getConfigValues().maxAuctionStorageDays();
        int limit = limitService.getLimit(player, currency);
        long now = System.currentTimeMillis();
        long cutoff = now - maxDays * 86_400_000L;

        List<AuctionItem> all = repository.findAll(currency);
        int count = 0;
        for (AuctionItem item : all) {
            if (item.sellerName().equalsIgnoreCase(player.getName())
                    && item.createdAt() >= cutoff
                    && item.status() == AuctionViewType.SELLING) {
                count++;
                if (count >= limit) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().limitReached(), configManager));
                    return false;
                }
            }
        }

        ItemStack soldItem = hand.clone();
        long id = repository.create(player.getName(), currency, soldItem, price);
        if (id > 0) {
            player.getInventory().setItemInMainHand(null);
            String symbol = currency.symbol(configManager.getConfigValues());
            String formatted = PriceFormatter.format(price) + " " + symbol;
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().sellSuccess().replace("{symbol_value}", formatted), configManager));
            return true;
        } else {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().databaseError(), configManager));
            return false;
        }
    }

    public boolean buy(Player buyer, long lotId, int amount) {
        if (!repository.markAsSelling(lotId)) {
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().noId(), configManager));
            return false;
        }

        Optional<AuctionItem> optItem = repository.findById(lotId);
        if (optItem.isEmpty()) {
            repository.restoreStatus(lotId);
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().noId(), configManager));
            return false;
        }

        AuctionItem item = optItem.get();

        if (item.sellerName().equals(buyer.getName())) {
            repository.restoreStatus(lotId);
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().noOwn(), configManager));
            return false;
        }

        if (item.expired(configManager.getConfigValues().maxAuctionStorageDays())) {
            repository.restoreStatus(lotId);
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().noId(), configManager));
            return false;
        }

        int buyAmount = Math.max(1, Math.min(amount, item.amount()));
        ItemStack bought = item.itemStackClone();
        bought.setAmount(buyAmount);

        if (!canFit(buyer, bought)) {
            repository.restoreStatus(lotId);
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().inventoryFull(), configManager));
            return false;
        }

        double totalPrice = item.pricePerItem() * buyAmount;

        if (!economyProvider.has(buyer, item.currency(), totalPrice)) {
            repository.restoreStatus(lotId);
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().nomoney(), configManager));
            return false;
        }

        if (!economyProvider.withdraw(buyer, item.currency(), totalPrice)) {
            repository.restoreStatus(lotId);
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().nomoney(), configManager));
            return false;
        }

        boolean deleted = repository.delete(lotId);
        if (!deleted) {
            economyProvider.deposit(buyer, item.currency(), totalPrice);
            repository.restoreStatus(lotId);
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().noId(), configManager));
            return false;
        }

        try {
            ItemStack boughtFinal = item.itemStackClone();
            boughtFinal.setAmount(buyAmount);
            buyer.getInventory().addItem(boughtFinal).values()
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
                    boughtFinal.getType().name(),
                    buyAmount,
                    totalPrice
            );

            String itemDisplayName = ItemUtils.getItemDisplayName(boughtFinal);
            String priceStr = PriceFormatter.format(totalPrice);
            String symbol = item.currency().symbol(configManager.getConfigValues());
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().yspex().replace("{symbol_value}", priceStr + " " + symbol), configManager));

            Player onlineSeller = Bukkit.getPlayer(item.sellerName());
            if (onlineSeller != null) {
                onlineSeller.sendMessage(PlaceholderUtils.applypapi(onlineSeller, configManager.getConfigValues().messages().buySeller()
                        .replace("{buyer}", buyer.getName())
                        .replace("{item_name}", itemDisplayName)
                        .replace("{amount}", String.valueOf(buyAmount))
                        .replace("{price}", priceStr)
                        .replace("{symbol_value}", symbol), configManager));
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при завершении покупки: " + e.getMessage());
            economyProvider.deposit(buyer, item.currency(), totalPrice);
            repository.restoreStatus(lotId);
            buyer.sendMessage(PlaceholderUtils.applypapi(buyer, configManager.getConfigValues().messages().databaseError(), configManager));
            return false;
        }
    }

    public boolean take(Player player, long lotId) {
        if (!repository.markAsSelling(lotId)) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
            return false;
        }

        Optional<AuctionItem> optItem = repository.findById(lotId);
        if (optItem.isEmpty()) {
            repository.restoreStatus(lotId);
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
            return false;
        }

        AuctionItem item = optItem.get();
        if (!item.sellerName().equalsIgnoreCase(player.getName())) {
            repository.restoreStatus(lotId);
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noOwn(), configManager));
            return false;
        }

        ItemStack returned = item.itemStackClone();
        if (!canFit(player, returned)) {
            repository.restoreStatus(lotId);
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().inventoryFull(), configManager));
            return false;
        }

        boolean deleted = repository.delete(lotId);
        if (!deleted) {
            repository.restoreStatus(lotId);
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().noId(), configManager));
            return false;
        }

        player.getInventory().addItem(returned).values().forEach(left ->
                player.getWorld().dropItemNaturally(player.getLocation(), left));

        String msg = item.expired(configManager.getConfigValues().maxAuctionStorageDays())
                ? configManager.getConfigValues().messages().takeExpired() : configManager.getConfigValues().messages().takeSelling();
        player.sendMessage(PlaceholderUtils.applypapi(player, msg, configManager));
        return true;
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
            int slotsNeeded = (amount + maxStack - 1) / maxStack;
            return emptySlots >= slotsNeeded;
        }
        return true;
    }
}