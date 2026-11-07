package ru.moscow.foxkiss.database.h2.impl;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

import ru.moscow.foxkiss.auction.*;
import ru.moscow.foxkiss.auction.AuctionRepository.*;
import static ru.moscow.foxkiss.database.h2.impl.H2ItemCodec.*;
import static ru.moscow.foxkiss.database.h2.impl.H2Storage.*;

public final class H2DeliveryStore {
    private final H2Storage storage;

    public H2DeliveryStore(H2Storage storage) {
        this.storage = storage;
    }

    public void confirmDelivery(long pendingDeliveryId, String operationId) {
        try (Connection conn = storage.open()) {
            conn.setAutoCommit(false);
            try {
                if (pendingDeliveryId > 0) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auction_pending_deliveries WHERE id=?")) {
                        ps.setLong(1, pendingDeliveryId);
                        ps.executeUpdate();
                    }
                }
                if (operationId != null && !operationId.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE auction_transaction_journal SET status=?, updated_at=? WHERE operation_id=?")) {
                        ps.setString(1, "COMPLETED");
                        ps.setLong(2, System.currentTimeMillis());
                        ps.setString(3, operationId);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                storage.plugin.getLogger().log(Level.WARNING, "Ошибка подтверждения выдачи #" + pendingDeliveryId, e);
            }
        } catch (SQLException e) {
            storage.plugin.getLogger().log(Level.WARNING, "Ошибка подключения БД при подтверждении выдачи #" + pendingDeliveryId, e);
        }
    }

    public void removePendingDelivery(long pendingDeliveryId) {
        try (Connection conn = storage.open();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM auction_pending_deliveries WHERE id=?")) {
            ps.setLong(1, pendingDeliveryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            storage.plugin.getLogger().log(Level.WARNING, "Ошибка удаления ожидающей выдачи #" + pendingDeliveryId, e);
        }
    }

    public List<PendingDelivery> getPendingDeliveries(String playerName) {
        ObjectList<PendingDelivery> list = new ObjectArrayList<>();
        try (Connection conn = storage.open();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM auction_pending_deliveries WHERE player_name=? ORDER BY id ASC")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String name = rs.getString("player_name");
                    AuctionLotType type = AuctionLotType.valueOf(rs.getString("lot_type"));
                    ItemStack item = deserialize(rs.getString("item"));
                    List<ItemStack> contents = deserializeInventory(rs.getString("inventory_data"));
                    long createdAt = rs.getLong("created_at");
                    list.add(new PendingDelivery(id, name, type, item, contents, createdAt, rs.getString("currency") == null ? null : AuctionCurrency.valueOf(rs.getString("currency"))));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось загрузить ожидающие доставки", e);
        }
        return list;
    }

    public List<ItemStack> getPendingInventoryItems(String playerName, AuctionCurrency currency) {
        try (Connection conn = storage.open();
             PreparedStatement ps = conn.prepareStatement("SELECT inventory_data FROM auction_pending_inventory_items WHERE player_name=? AND currency=?")) {
            ps.setString(1, playerName);
            ps.setString(2, currency.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return deserializeInventory(rs.getString("inventory_data"));
                }
            }
        } catch (Exception e) {
            storage.plugin.getLogger().log(Level.WARNING, "Ошибка загрузки невыданных предметов игрока " + playerName, e);
        }
        return new ObjectArrayList<>();
    }

    public boolean replacePendingInventoryItems(String playerName, AuctionCurrency currency, List<ItemStack> items) {
        try (Connection conn = storage.open()) {
            conn.setAutoCommit(false);
            try {
                replacePendingInventoryItems(conn, playerName, currency, items);
                conn.commit();
                return true;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw e;
            }
        } catch (Exception e) {
            storage.plugin.getLogger().log(Level.WARNING, "Ошибка сохранения невыданных предметов игрока " + playerName, e);
            return false;
        }
    }

    public boolean completeInventoryDelivery(long pendingDeliveryId, String operationId, String playerName, AuctionCurrency currency, List<ItemStack> items) {
        return completeInventoryDeliveries(playerName, List.of(new DeliveryConfirmation(pendingDeliveryId, currency, items)));
    }

    public boolean completeInventoryDeliveries(String playerName, List<DeliveryConfirmation> deliveries) {
        try (Connection conn = storage.open()) {
            conn.setAutoCommit(false);
            try {
                for (AuctionCurrency currency : AuctionCurrency.values()) {
                    List<ItemStack> combined = null;
                    boolean locked = false;
                    for (DeliveryConfirmation delivery : deliveries) {
                        if (delivery.currency() != currency) continue;
                        if (!locked) { lockSeller(conn, playerName, currency); locked = true; }
                        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM auction_pending_deliveries WHERE id=? AND player_name=? AND currency=? FOR UPDATE")) {
                            ps.setLong(1, delivery.id()); ps.setString(2, playerName); ps.setString(3, currency.name());
                            try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) continue; }
                        }
                        if (!delivery.items().isEmpty()) {
                            if (combined == null) {
                                combined = new ArrayList<>();
                                try (PreparedStatement ps = conn.prepareStatement("SELECT inventory_data FROM auction_pending_inventory_items WHERE player_name=? AND currency=? FOR UPDATE")) {
                                    ps.setString(1, playerName); ps.setString(2, currency.name());
                                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) combined.addAll(deserializeInventory(rs.getString(1))); }
                                }
                            }
                            combined.addAll(delivery.items());
                        }
                        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auction_pending_deliveries WHERE id=?")) {
                            ps.setLong(1, delivery.id()); ps.executeUpdate();
                        }
                    }
                    if (combined != null) replacePendingInventoryItems(conn, playerName, currency, combined);
                }
                conn.commit();
                return true;
            } catch (Exception e) { conn.rollback(); throw e; }
        } catch (Exception e) {
            storage.plugin.getLogger().log(Level.SEVERE, "Не удалось подтвердить пакет доставок игрока " + playerName, e);
            return false;
        }
    }

    public void savePendingDelivery(String playerName, AuctionCurrency currency, List<ItemStack> items) {
        if (items.isEmpty()) return;
        try (Connection conn = storage.open(); PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO auction_pending_deliveries(player_name,lot_type,item,inventory_data,created_at,currency) VALUES(?,'INVENTORY',?,?,?,?)")) {
            ps.setString(1, playerName);
            ps.setString(2, serialize(items.getFirst()));
            ps.setString(3, serializeInventory(items));
            ps.setLong(4, System.currentTimeMillis());
            ps.setString(5, currency.name());
            ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Не удалось сохранить возвращаемые предметы", e); }
    }

    public void claimPendingInventoryItems(String playerName, AuctionCurrency currency) {
        try (Connection conn = storage.open()) {
            conn.setAutoCommit(false);
            try {
                lockSeller(conn, playerName, currency);
                String data = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT inventory_data FROM auction_pending_inventory_items WHERE player_name=? AND currency=? FOR UPDATE")) {
                    ps.setString(1, playerName); ps.setString(2, currency.name());
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) data = rs.getString(1); }
                }
                if (data != null && !data.isEmpty()) {
                    String icon = null;
                    for (String part : data.split(";", -1)) {
                        if (!part.isEmpty()) {
                            icon = part;
                            break;
                        }
                    }
                    if (icon == null) throw new IllegalStateException("В очереди инвентаря отсутствуют предметы");
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO auction_pending_deliveries(player_name,lot_type,item,inventory_data,created_at,currency) VALUES(?,'INVENTORY',?,?,?,?)")) {
                        ps.setString(1, playerName); ps.setString(2, icon); ps.setString(3, data);
                        ps.setLong(4, System.currentTimeMillis()); ps.setString(5, currency.name()); ps.executeUpdate();
                    }
                    replacePendingInventoryItems(conn, playerName, currency, List.of());
                }
                conn.commit();
            } catch (Exception e) { conn.rollback(); throw e; }
        } catch (Exception e) { throw new IllegalStateException("Не удалось зарезервировать предметы", e); }
    }

    private void replacePendingInventoryItems(Connection conn, String playerName, AuctionCurrency currency, List<ItemStack> items) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auction_pending_inventory_items WHERE player_name=? AND currency=?")) {
            ps.setString(1, playerName);
            ps.setString(2, currency.name());
            ps.executeUpdate();
        }

        if (items.isEmpty()) {
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO auction_pending_inventory_items(player_name,currency,inventory_data,updated_at) VALUES(?,?,?,?)")) {
            ps.setString(1, playerName);
            ps.setString(2, currency.name());
            ps.setString(3, serializeInventory(items));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
