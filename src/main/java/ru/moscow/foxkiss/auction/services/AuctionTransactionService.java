package ru.moscow.foxkiss.auction.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.scheduler.SchedulerService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public final class AuctionTransactionService {

    private final SchedulerService scheduler;
    private final AuctionRepository repository;
    private final EconomyProvider economyProvider;
    private final ItemDisplayFactory itemFactory;
    private final Set<UUID> pendingListings = new HashSet<>();

    public boolean beginListing(Player player) {
        return pendingListings.add(player.getUniqueId());
    }

    public void endListing(Player player) {
        pendingListings.remove(player.getUniqueId());
    }

    public boolean withdrawMoney(Player player, AuctionCurrency currency, double amount) {
        return !economyProvider.withdraw(player, currency, amount);
    }

    public boolean hasEnoughMoney(Player player, AuctionCurrency currency, double amount) {
        return economyProvider.has(player, currency, amount);
    }

    public boolean depositMoney(OfflinePlayer player, AuctionCurrency currency, double amount) {
        return economyProvider.deposit(player, currency, amount);
    }

    public void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack left : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    public List<ItemStack> giveItems(Player player, List<ItemStack> items) {
        List<ItemStack> leftovers = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            Map<Integer, ItemStack> remaining = player.getInventory().addItem(item.clone());
            for (ItemStack left : remaining.values()) {
                leftovers.add(left.clone());
            }
        }

        return leftovers;
    }
}
