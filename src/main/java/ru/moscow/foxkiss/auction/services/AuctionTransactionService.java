package ru.moscow.foxkiss.auction.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.moscow.foxkiss.auction.*;
import ru.moscow.foxkiss.auction.services.sessions.DeliverySession;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.EconomyProvider;
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.*;

@Getter
@RequiredArgsConstructor
public final class AuctionTransactionService {
    private final SchedulerService scheduler;
    private final AuctionRepository repository;
    private final EconomyProvider economyProvider;
    private final ItemDisplayFactory itemFactory;
    private final Set<UUID> pendingListings = new HashSet<>();
    private final Set<UUID> delivering = new HashSet<>();
    @Getter
    private final Map<UUID, DeliverySession> sessions = new HashMap<>();
    private static final NamespacedKey receipte = new NamespacedKey("fmauction", "delivery_receipt");

    public void deliverPending(Player player, AuctionCurrency currency, IConfigManager config) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        synchronized (delivering) {
            DeliverySession running = sessions.get(uuid);
            if (running != null) {
                running.player = player;
                if (currency == null) { running.rescan = true; running.allRequested = true; }
                else running.claims.add(currency);
                return;
            }
        }
        Receipt receipt = readReceipt(player);
        DeliverySession session = new DeliverySession(uuid, player.getName(), player, config, receipt);
        if (currency != null) session.claims.add(currency);
        else session.allRequested = true;
        synchronized (delivering) {
            sessions.put(uuid, session);
            delivering.add(uuid);
        }
        loadDeliveries(session);
    }

    public void onPlayerQuit(Player player) {
        DeliverySession session;
        synchronized (delivering) {
            session = sessions.get(player.getUniqueId());
            if (session == null || session.player != player) return;
            session.player = null;
            if (session.inFlight) return;
        }
        finishDelivery(session);
    }

    private boolean isCurrent(DeliverySession session) {
        synchronized (delivering) { return sessions.get(session.uuid) == session; }
    }

    private void loadDeliveries(DeliverySession session) {
        EnumSet<AuctionCurrency> claims;
        synchronized (delivering) {
            if (sessions.get(session.uuid) != session) return;
            claims = session.claims.clone();
            session.claims.clear();
            session.currencies = claims;
            session.all = session.allRequested;
            session.allRequested = false;
            session.commandBatch = !session.all && !claims.isEmpty();
            session.deliveredAny = false;
            session.rescan = false;
            session.inFlight = true;
        }
        try {
            scheduler.runAsync(() -> {
                try {
                    for (AuctionCurrency currency : claims) repository.claimPendingInventoryItems(session.name, currency);
                    session.pending = repository.getPendingDeliveries(session.name);
                    session.index = 0;
                    session.receiptPending = true;
                    session.prioritizedId = session.receipt == null ? -1 : session.receipt.deliveryId();
                    session.inFlight = false;
                    if (session.player == null) { finishDelivery(session); return; }
                    scheduleNext(session);
                } catch (RuntimeException e) { finishDelivery(session); throw e; }
            });
        } catch (RuntimeException e) { finishDelivery(session); throw e; }
    }

    private void scheduleNext(DeliverySession session) {
        try {
            scheduler.runSync(() -> {
                if (!isCurrent(session)) return;
                try { deliverNext(session); }
                catch (RuntimeException e) { finishDelivery(session); throw e; }
            });
        } catch (RuntimeException e) { finishDelivery(session); throw e; }
    }

    private void deliverNext(DeliverySession session) {
        Player player = session.player;
        if (player == null || !player.isOnline()) { finishDelivery(session); return; }
        PendingDelivery next = null;
        if (session.receiptPending && session.receipt != null) {
            for (PendingDelivery candidate : session.pending) {
                if (candidate.id() == session.prioritizedId) { next = candidate; break; }
            }
        }
        session.receiptPending = false;
        while (next == null && session.index < session.pending.size()) {
            PendingDelivery candidate = session.pending.get(session.index++);
            if (session.receipt != null && candidate.id() == session.prioritizedId) continue;
            if (candidate.currency() != null && (session.all || session.currencies.contains(candidate.currency()))) next = candidate;
        }
        if (next != null) {
            session.deliveredAny = true;
            processDelivery(session, player, next);
            return;
        }
        boolean more;
        synchronized (delivering) { more = session.rescan || !session.claims.isEmpty(); }
        if (more) loadDeliveries(session);
        else {
            try {
                if (session.commandBatch && !session.deliveredAny) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, session.config.getConfigValues().messages().pendingItemsEmpty(), session.config));
                }
            } finally { finishDelivery(session); }
        }
    }

    private void processDelivery(DeliverySession session, Player player, PendingDelivery delivery) {
        if (delivery.currency() == null) throw new IllegalStateException("Для старой доставки необходимо установить валюту: " + delivery.id());
        Receipt receipt = readReceipt(player);
        List<ItemStack> contents = delivery.lotType() == AuctionLotType.INVENTORY ? delivery.inventoryContents() : List.of(delivery.item());
        boolean replay = receipt != null && receipt.deliveryId() == delivery.id();
        List<ItemStack> leftovers;
        if (replay) {
            leftovers = receipt.leftovers();
            validateRemainder(contents, leftovers);
        } else {
            ItemStack[] before = snapshotStorage(player);
            String previous = player.getPersistentDataContainer().get(receipte, PersistentDataType.STRING);
            try {
                leftovers = giveItems(player, contents);
                String encoded = encodeItems(leftovers);
                player.getPersistentDataContainer().set(receipte, PersistentDataType.STRING, encodeReceipt(delivery.id(), encoded));
            } catch (RuntimeException e) {
                player.getInventory().setStorageContents(before);
                if (previous == null) player.getPersistentDataContainer().remove(receipte);
                else player.getPersistentDataContainer().set(receipte, PersistentDataType.STRING, previous);
                throw e;
            }
            player.saveData();
        }
        session.receipt = new Receipt(delivery.id(), leftovers);
        long received = replay ? 0 : countItems(contents) - countItems(leftovers);
        confirmDelivery(session, delivery, List.copyOf(leftovers), received);
    }

    private void confirmDelivery(DeliverySession session, PendingDelivery delivery, List<ItemStack> leftovers, long received) {
        session.inFlight = true;
        try {
            scheduler.runAsync(() -> {
                try {
                    boolean confirmed = repository.completeInventoryDelivery(delivery.id(), null, session.name, delivery.currency(), leftovers);
                    session.inFlight = false;
                    if (!confirmed || session.player == null) { finishDelivery(session); return; }
                    scheduler.runSync(() -> {
                        if (!isCurrent(session)) return;
                        try {
                            Player player = session.player;
                            if (player == null || !player.isOnline()) { finishDelivery(session); return; }
                            notifyDelivery(player, session, delivery.currency(), leftovers, received);
                            deliverNext(session);
                        } catch (RuntimeException e) { finishDelivery(session); throw e; }
                    });
                } catch (RuntimeException e) { finishDelivery(session); throw e; }
            });
        } catch (RuntimeException e) { finishDelivery(session); throw e; }
    }

    private void finishDelivery(DeliverySession session) {
        synchronized (delivering) {
            if (sessions.get(session.uuid) != session) return;
            sessions.remove(session.uuid);
            delivering.remove(session.uuid);
        }
    }

    private Receipt readReceipt(Player player) {
        String raw = player.getPersistentDataContainer().get(receipte, PersistentDataType.STRING);
        if (raw == null && player.getPersistentDataContainer().has(receipte)) {
            throw new IllegalStateException("Неверный тип доставки; необходима сверка");
        }
        return parseReceipt(raw);
    }

    private Receipt parseReceipt(String raw) {
        if (raw == null) return null;
        try {
            if (raw.startsWith("v2:")) {
                int checksumAt = raw.lastIndexOf(':');
                if (checksumAt <= 3) throw new IllegalArgumentException("Отсутствует контрольная сумма");
                String body = raw.substring(3, checksumAt);
                if (!receiptChecksum(body).equals(raw.substring(checksumAt + 1))) {
                    throw new IllegalArgumentException("Контрольная сумма не совпадает");
                }
                raw = body;
            }
            int separator = raw.indexOf(':');
            if (separator <= 0) throw new IllegalArgumentException("Отсутствует идентификатор");
            for (int i = 0; i < separator; i++) {
                char c = raw.charAt(i);
                if (c < '0' || c > '9') throw new IllegalArgumentException("Некорректный идентификатор");
            }
            long id = Long.parseLong(raw.substring(0, separator));
            if (id <= 0) throw new IllegalArgumentException("Идентификатор должен быть положительным");
            return new Receipt(id, decodeItems(raw.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Квитанция повреждена; выдача остановлена до сверки", e);
        }
    }

    private static String encodeReceipt(long id, String payload) {
        String body = id + ":" + payload;
        return "v2:" + body + ":" + receiptChecksum(body);
    }

    private static String receiptChecksum(String body) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Алгоритм SHA-256 недоступен", e);
        }
    }

    private static void validateRemainder(List<ItemStack> contents, List<ItemStack> leftovers) {
        int[] available = new int[contents.size()];
        for (int i = 0; i < available.length; i++) {
            ItemStack item = contents.get(i);
            if (item != null) available[i] = item.getAmount();
        }
        for (ItemStack left : leftovers) {
            int needed = left.getAmount();
            for (int i = 0; i < available.length && needed > 0; i++) {
                if (available[i] > 0 && left.isSimilar(contents.get(i))) {
                    int take = Math.min(needed, available[i]);
                    needed -= take;
                    available[i] -= take;
                }
            }
            if (needed != 0) throw new IllegalStateException("Остаток квитанции превышает содержимое доставки");
        }
    }

    private static ItemStack[] snapshotStorage(Player player) {
        ItemStack[] items = player.getInventory().getStorageContents();
        for (int i = 0; i < items.length; i++) if (items[i] != null) items[i] = items[i].clone();
        return items;
    }

    private static long countItems(List<ItemStack> items) {
        long count = 0;
        for (ItemStack item : items) if (item != null) count += item.getAmount();
        return count;
    }

    private void notifyDelivery(Player player, DeliverySession session, AuctionCurrency currency, List<ItemStack> items, long received) {
        IConfigManager config = session.config;
        if (session.commandBatch && received > 0) {
            player.sendMessage(PlaceholderUtils.applypapi(player, config.getConfigValues().messages().pendingItemsReceived()
                    .replace("{amount}", String.valueOf(received)), config));
        }
        if (items.isEmpty()) return;
        String message = session.commandBatch ? config.getConfigValues().messages().pendingItemsRemaining()
                : config.getConfigValues().messages().pendingItemsSaved();
        player.sendMessage(PlaceholderUtils.applypapi(player, message
                .replace("{amount}", String.valueOf(countItems(items)))
                .replace("{command}", currency == AuctionCurrency.VAULT ? "/ah items" : "/dah items"), config));
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
        String[] parts = data.split(";", -1);
        List<ItemStack> items = new ArrayList<>(parts.length);
        Base64.Decoder decoder = Base64.getDecoder();
        for (String part : parts) {
            if (part.isEmpty()) throw new IllegalArgumentException("Пустая запись предмета");
            ItemStack item = ItemStack.deserializeBytes(decoder.decode(part));
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) throw new IllegalArgumentException("Некорректный предмет");
            items.add(item);
        }
        return List.copyOf(items);
    }

    public record Receipt(long deliveryId, List<ItemStack> leftovers) {}

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

    public void returnItems(Player player, AuctionCurrency currency, List<ItemStack> items, IConfigManager config) {
        String name = player.getName();
        List<ItemStack> returned = List.copyOf(items);
        scheduler.runAsync(() -> {
            repository.savePendingDelivery(name, currency, returned);
            scheduler.runSync(() -> deliverPending(player, null, config));
        });
    }

    public List<ItemStack> giveItems(Player player, List<ItemStack> items) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(item.clone());
            for (ItemStack left : remaining.values()) leftovers.add(left);
        }
        return leftovers;
    }
}