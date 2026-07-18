package ru.moscow.foxkiss.auction;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class SQLiteAuctionRepository implements AuctionRepository {

    private final JavaPlugin plugin;
    private final String jdbcUrl;

    public SQLiteAuctionRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        Path dbPath = plugin.getDataFolder().toPath().resolve("auction.db");
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    @Override
    public void init() {
        plugin.getDataFolder().mkdirs();
        try (Connection conn = open(); Statement st = conn.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS auction_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        seller_name TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        item TEXT NOT NULL,
                        price REAL NOT NULL,
                        created_at INTEGER NOT NULL,
                        status INTEGER DEFAULT 0,
                        material TEXT
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sales_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        seller_name TEXT NOT NULL,
                        buyer_name TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        item_type TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        price REAL NOT NULL,
                        sold_at INTEGER NOT NULL
                    )
                    """);
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sales_currency_time ON sales_history(currency, sold_at)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sales_seller ON sales_history(seller_name, currency)");
            try {
                st.executeUpdate("ALTER TABLE auction_items ADD COLUMN material TEXT");
            } catch (SQLException ignored) { /* колонка уже существует */ }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    @Override
    public CompletableFuture<Long> create(String sellerName, AuctionCurrency currency, ItemStack itemStack, double price) {
        return runAsync(() -> {
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO auction_items(seller_name,currency,item,price,created_at,status,material) VALUES(?,?,?,?,?,0,?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, sellerName);
                ps.setString(2, currency.name());
                ps.setString(3, serialize(itemStack));
                ps.setDouble(4, price);
                ps.setLong(5, System.currentTimeMillis());
                ps.setString(6, itemStack.getType().name());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Long> createIfAllowed(String sellerName, AuctionCurrency currency, ItemStack itemStack, double price, int maxDays, int limit) {
        return runAsync(() -> {
            long minCreatedAt = System.currentTimeMillis() - maxDays * 86_400_000L;
            try (Connection conn = open()) {
                conn.setAutoCommit(false);
                try (PreparedStatement countStmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM auction_items WHERE seller_name=? AND currency=? AND created_at>? AND status=0")) {
                    countStmt.setString(1, sellerName);
                    countStmt.setString(2, currency.name());
                    countStmt.setLong(3, minCreatedAt);
                    try (ResultSet rs = countStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) >= limit) {
                            conn.rollback();
                            return -1L;
                        }
                    }
                }
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO auction_items(seller_name,currency,item,price,created_at,status,material) VALUES(?,?,?,?,?,0,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insertStmt.setString(1, sellerName);
                    insertStmt.setString(2, currency.name());
                    insertStmt.setString(3, serialize(itemStack));
                    insertStmt.setDouble(4, price);
                    insertStmt.setLong(5, System.currentTimeMillis());
                    insertStmt.setString(6, itemStack.getType().name());
                    insertStmt.executeUpdate();
                    try (ResultSet keys = insertStmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            conn.commit();
                            return keys.getLong(1);
                        } else {
                            conn.rollback();
                            return -1L;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Transaction failed", e);
                return -1L;
            }
        });
    }

    @Override
    public CompletableFuture<List<AuctionItem>> findAll(AuctionCurrency currency) {
        return runAsync(() -> {
            List<AuctionItem> items = new ArrayList<>();
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM auction_items WHERE currency=? AND status=0")) {
                ps.setString(1, currency.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(readItem(rs));
                    }
                }
            }
            return items;
        });
    }

    @Override
    public CompletableFuture<Optional<AuctionItem>> findById(long id) {
        return runAsync(() -> {
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM auction_items WHERE id=?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readItem(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(long id) {
        return runAsync(() -> {
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM auction_items WHERE id=?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Void> recordSale(String sellerName, String buyerName, AuctionCurrency currency,
                                              String itemType, int amount, double price) {
        return runAsync(() -> {
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO sales_history(seller_name,buyer_name,currency,item_type,amount,price,sold_at) VALUES(?,?,?,?,?,?,?)")) {
                ps.setString(1, sellerName);
                ps.setString(2, buyerName);
                ps.setString(3, currency.name());
                ps.setString(4, itemType);
                ps.setInt(5, amount);
                ps.setDouble(6, price);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> markAsSelling(long id) {
        return runAsync(() -> {
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE auction_items SET status=1 WHERE id=? AND status=0")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Void> restoreStatus(long id) {
        return runAsync(() -> {
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE auction_items SET status=0 WHERE id=? AND status=1")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<TopSeller>> getTopSellers(AuctionCurrency currency, int limit) {
        return runAsync(() -> {
            List<TopSeller> sellers = new ArrayList<>();
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT seller_name, COUNT(*) as sold, COALESCE(SUM(price), 0) as earned " +
                                 "FROM sales_history WHERE currency=? GROUP BY seller_name ORDER BY sold DESC LIMIT ?")) {
                ps.setString(1, currency.name());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sellers.add(new TopSeller(
                                rs.getString("seller_name"),
                                rs.getInt("sold"),
                                rs.getDouble("earned")
                        ));
                    }
                }
            }
            return sellers;
        });
    }

    @Override
    public CompletableFuture<PlayerStats> getPlayerStats(String playerName, AuctionCurrency currency) {
        return runAsync(() -> {
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT COUNT(*) as sold, COALESCE(SUM(price), 0) as earned " +
                                 "FROM sales_history WHERE seller_name=? AND currency=?")) {
                ps.setString(1, playerName);
                ps.setString(2, currency.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerStats(rs.getInt("sold"), rs.getDouble("earned"));
                    }
                    return new PlayerStats(0, 0.0);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> getUniqueMaterialNames(AuctionCurrency currency) {
        return runAsync(() -> {
            List<String> names = new ArrayList<>();
            try (Connection conn = open();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT DISTINCT material FROM auction_items WHERE currency=? AND status=0 AND material IS NOT NULL")) {
                ps.setString(1, currency.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        names.add(rs.getString("material"));
                    }
                }
            }
            return names;
        });
    }

    @Override
    public void close() {
    }

    private AuctionItem readItem(ResultSet rs) throws Exception {
        return new AuctionItem(
                rs.getLong("id"),
                rs.getString("seller_name"),
                AuctionCurrency.valueOf(rs.getString("currency")),
                deserialize(rs.getString("item")),
                rs.getDouble("price"),
                rs.getLong("created_at")
        );
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private <T> CompletableFuture<T> runAsync(SqlCallable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = callable.call();
                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Auction storage async error", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private String serialize(ItemStack itemStack) throws Exception {
        return Base64.getEncoder().encodeToString(itemStack.serializeAsBytes());
    }

    private ItemStack deserialize(String data) throws Exception {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
    }

    @FunctionalInterface
    private interface SqlCallable<T> {
        T call() throws Exception;
    }
}