package ru.moscow.foxkiss.auction.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import ru.moscow.foxkiss.auction.PendingDelivery;
import ru.moscow.foxkiss.auction.AuctionLotType;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import java.util.Base64;
import java.util.Collections;
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

    private final Set<UUID> delivering = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> deliveryRequested = Collections.synchronizedSet(new HashSet<>());
    private static final NamespacedKey RECEIPT = new NamespacedKey("fmauction", "delivery_receipt");

    public void deliverPending(Player player, AuctionCurrency claimCurrency, IConfigManager config) {
        UUID uuid = player.getUniqueId();
        if (!delivering.add(uuid)) {
            if (claimCurrency == null) deliveryRequested.add(uuid);
            return;
        }
        String name = player.getName();
        String receipt = player.getPersistentDataContainer().get(RECEIPT, PersistentDataType.STRING);
        long receiptId = receipt == null ? -1 : Long.parseLong(receipt.substring(0, receipt.indexOf(':')));
        scheduler.runAsync(() -> {
            try {
                if (claimCurrency != null) repository.claimPendingInventoryItems(name, claimCurrency);
                deliverNext(player, name, config, claimCurrency, receiptId, claimCurrency != null);
            } catch (RuntimeException e) { delivering.remove(uuid); throw e; }
        });
    }

    private void deliverNext(Player player, String name, IConfigManager config, AuctionCurrency filter, long receiptId, boolean notifyEmpty) {
        List<PendingDelivery> pending = repository.getPendingDeliveries(name);
        PendingDelivery selected = null;
        for (PendingDelivery candidate : pending) {
            if (candidate.id() == receiptId) {
                selected = candidate;
                break;
            }
            if (selected == null && candidate.currency() != null
                    && (filter == null || candidate.currency() == filter)) {
                selected = candidate;
            }
        }
        PendingDelivery delivery = selected;
        scheduler.runSync(() -> {
            UUID uuid = player.getUniqueId();
            if (delivery == null || !player.isOnline()) {
                if (delivery == null && notifyEmpty && player.isOnline()) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, config.getConfigValues().messages().pendingItemsEmpty(), config));
                }
                delivering.remove(uuid);
                if (deliveryRequested.remove(uuid) && player.isOnline()) deliverPending(player, null, config);
                return;
            }
            if (delivery.currency() == null) {
                delivering.remove(uuid);
                throw new IllegalStateException("Legacy delivery needs currency reconciliation: " + delivery.id());
            }
            String prefix = delivery.id() + ":";
            String receipt = player.getPersistentDataContainer().get(RECEIPT, PersistentDataType.STRING);
            List<ItemStack> contents = delivery.lotType() == AuctionLotType.INVENTORY
                    ? delivery.inventoryContents() : List.of(delivery.item());
            boolean replay = receipt != null && receipt.startsWith(prefix);
            List<ItemStack> leftovers;
            if (replay) {
                leftovers = decodeItems(receipt.substring(prefix.length()));
            } else {
                ItemStack[] before = player.getInventory().getContents();
                for (int i = 0; i < before.length; i++) if (before[i] != null) before[i] = before[i].clone();
                try {
                    leftovers = giveItems(player, contents);
                    player.getPersistentDataContainer().set(RECEIPT, PersistentDataType.STRING, prefix + encodeItems(leftovers));
                    player.saveData();
                } catch (RuntimeException e) {
                    player.getInventory().setContents(before);
                    if (receipt == null) player.getPersistentDataContainer().remove(RECEIPT);
                    else player.getPersistentDataContainer().set(RECEIPT, PersistentDataType.STRING, receipt);
                    delivering.remove(uuid);
                    throw e;
                }
            }
            List<ItemStack> stored = List.copyOf(leftovers);
            scheduler.runAsync(() -> {
                try {
                    if (!repository.completeInventoryDelivery(delivery.id(), null, name, delivery.currency(), stored)) {
                        delivering.remove(uuid);
                        return;
                    }
                    scheduler.runSync(() -> {
                        if (!player.isOnline()) return;
                        int remaining = 0;
                        for (ItemStack item : stored) remaining += item.getAmount();
                        if (filter != null && !replay) {
                            int received = -remaining;
                            for (ItemStack item : contents) {
                                if (item != null) received += item.getAmount();
                            }
                            if (received > 0) player.sendMessage(PlaceholderUtils.applypapi(player,
                                    config.getConfigValues().messages().pendingItemsReceived().replace("{amount}", String.valueOf(received)), config));
                        }
                        if (!stored.isEmpty()) {
                            String message = filter == null ? config.getConfigValues().messages().pendingItemsSaved()
                                    : config.getConfigValues().messages().pendingItemsRemaining();
                            player.sendMessage(PlaceholderUtils.applypapi(player, message
                                    .replace("{amount}", String.valueOf(remaining))
                                    .replace("{command}", delivery.currency() == AuctionCurrency.VAULT ? "/ah items" : "/dah items"), config));
                        }
                    });
                    deliverNext(player, name, config, filter, -1, false);
                } catch (RuntimeException e) { delivering.remove(uuid); throw e; }
            });
        });
    }

    private static String encodeItems(List<ItemStack> items) {
        StringBuilder encoded = new StringBuilder();
        Base64.Encoder encoder = Base64.getEncoder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) encoded.append(';');
            encoded.append(encoder.encodeToString(items.get(i).serializeAsBytes()));
        }
        return encoded.toString();
    }

    private static List<ItemStack> decodeItems(String data) {
        if (data.isEmpty()) return List.of();
        String[] parts = data.split(";");
        List<ItemStack> items = new ArrayList<>(parts.length);
        Base64.Decoder decoder = Base64.getDecoder();
        for (String part : parts) items.add(ItemStack.deserializeBytes(decoder.decode(part)));
        return List.copyOf(items);
    }

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
