package ru.moscow.foxkiss.database.h2.impl;

import ru.moscow.foxkiss.database.h2.SqlRuntime;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.utils.TextUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import ru.moscow.foxkiss.auction.*;
import ru.moscow.foxkiss.auction.AuctionRepository.*;
import static ru.moscow.foxkiss.database.h2.impl.H2ItemCodec.*;

public final class H2MenuStore {
    private static final int active = 0;
    private final JavaPlugin plugin;
    private final DataSource dataSource;
    private final SqlRuntime sqlRuntime;
    private final boolean separatePageCount;

    public H2MenuStore(JavaPlugin plugin, DataSource dataSource, SqlRuntime sqlRuntime, boolean separatePageCount) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.sqlRuntime = sqlRuntime;
        this.separatePageCount = separatePageCount;
    }

    private final Map<PageKey, CachedPage> pages = new HashMap<>();
    private final Map<PageKey, CompletableFuture<MenuData>> pageFlights = new HashMap<>();
    private final Map<PageKey, CachedRows> rowPages = new HashMap<>();
    private final Map<PageKey, CompletableFuture<MenuQueryResult>> rowFlights = new HashMap<>();
    private record CachedRows(MenuQueryResult data, long expiresAt) {}
    private record PageKey(AuctionCurrency currency, String player, int days, int page, int size, AuctionSort sort, String category, String seller, String search, AuctionViewType view, long revision, Object translations) {}
    private record CachedPage(MenuData data, long expiresAt) {}

    public void clearReadCache() {
        synchronized (pages) {
            sqlRuntime.invalidateCache();
            pages.clear(); rowPages.clear();
        }
    }

    public MenuData loadMenuData(AuctionCurrency currency, String playerName, int maxDays, int page, int pageSize, AuctionSort sort, String category, String sellerFilter, String searchFilter, AuctionViewType viewType) {
        long revision = sqlRuntime.cacheRevision();
        PageKey key = new PageKey(currency, playerName, maxDays, page, pageSize, sort, category, sellerFilter,
                searchFilter, viewType, revision < 0 ? -System.nanoTime() : revision, ru.moscow.foxkiss.utils.ItemUtils.getSearchIndex());
        CompletableFuture<MenuData> flight;
        boolean owner;
        synchronized (pages) {
            CachedPage cached = pages.get(key);
            if (cached != null && cached.expiresAt > System.currentTimeMillis() && revision == sqlRuntime.cacheRevision()) return copyMenuData(cached.data);
            flight = pageFlights.get(key);
            owner = flight == null;
            if (owner) {
                flight = new CompletableFuture<>();
                pageFlights.put(key, flight);
            }
        }
        if (!owner) return copyMenuData(flight.join());
        try {
            LoadedMenu loaded = loadMenuDataUncached(currency, playerName, maxDays, page, pageSize, sort, category, sellerFilter, searchFilter, viewType);
            MenuData data = loaded.data;
            long expires = loaded.expiresAt;
            synchronized (pages) {
                if (revision >= 0 && revision == sqlRuntime.cacheRevision() && expires > System.currentTimeMillis()) {
                    if (pages.size() >= 128) {
                        Iterator<PageKey> iterator = pages.keySet().iterator();
                        iterator.next(); iterator.remove();
                    }
                    pages.put(key, new CachedPage(data, expires));
                }
            }
            flight.complete(data);
            return copyMenuData(data);
        } catch (Throwable e) {
            flight.completeExceptionally(e);
            throw e;
        } finally {
            synchronized (pages) { pageFlights.remove(key, flight); }
        }
    }

    private long pageExpiry(AuctionCurrency currency, int maxDays, long cutoff) {
        long now = System.currentTimeMillis();
        if (maxDays <= 0) return now;
        long storage = maxDays * 86_400_000L;
        try (Connection conn = open(); PreparedStatement ps = conn.prepareStatement(
                "SELECT MIN(created_at) FROM auction_items WHERE currency=? AND status=0 AND created_at>=?")) {
            ps.setString(1, currency.name()); ps.setLong(2, cutoff);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next(); long created = rs.getLong(1);
                return rs.wasNull() ? Long.MAX_VALUE : created + storage;
            }
        } catch (SQLException e) { return now; }
    }

    private static MenuData copyMenuData(MenuData source) {
        List<AuctionItem> items = new ArrayList<>(source.items().size());
        for (AuctionItem item : source.items()) {
            List<ItemStack> contents = new ArrayList<>();
            if (item.inventoryContents() != null) {
                for (ItemStack content : item.inventoryContents()) contents.add(content == null ? null : content.clone());
            }
            items.add(new AuctionItem(item.id(), item.sellerName(), item.currency(), item.itemStack().clone(), item.price(),
                    item.createdAt(), item.status(), item.type(), Collections.unmodifiableList(contents), item.operationId(), item.processingStartedAt()));
        }
        return new MenuData(List.copyOf(items), source.totalCount(), source.sellingCount(), source.expiredCount(), Map.copyOf(source.profiles()));
    }

    private MenuQueryResult sharedRows(AuctionCurrency currency, String player, int days, int page, int size,
                                      AuctionSort sort, String category, String seller, String search, AuctionViewType view, long cutoff) {
        long revision = sqlRuntime.cacheRevision();
        PageKey key = new PageKey(currency, view == AuctionViewType.MAIN ? "" : player, days, page, size, sort,
                category, seller, search, view, revision < 0 ? -System.nanoTime() : revision, ru.moscow.foxkiss.utils.ItemUtils.getSearchIndex());
        CompletableFuture<MenuQueryResult> flight;
        boolean owner;
        synchronized (pages) {
            CachedRows cached = rowPages.get(key);
            if (cached != null && cached.expiresAt > System.currentTimeMillis() && revision == sqlRuntime.cacheRevision()) return cached.data;
            flight = rowFlights.get(key);
            owner = flight == null;
            if (owner) { flight = new CompletableFuture<>(); rowFlights.put(key, flight); }
        }
        if (!owner) return flight.join();
        try {
            MenuQueryResult result;
            try (Connection conn = open()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                conn.setAutoCommit(false);
                result = findPageAndCount(conn, currency, page, size, sort, category, seller, search, view, cutoff, player);
                conn.commit();
            }
            long expiry = pageExpiry(currency, days, cutoff);
            result = new MenuQueryResult(result.items(), result.totalCount(), expiry);
            synchronized (pages) {
                if (revision >= 0 && revision == sqlRuntime.cacheRevision() && expiry > System.currentTimeMillis()) {
                    if (rowPages.size() >= 128) { Iterator<PageKey> keys = rowPages.keySet().iterator(); keys.next(); keys.remove(); }
                    rowPages.put(key, new CachedRows(result, expiry));
                }
            }
            flight.complete(result);
            return result;
        } catch (Throwable e) {
            flight.completeExceptionally(e);
            if (e instanceof RuntimeException failure) throw failure;
            if (e instanceof Error failure) throw failure;
            throw new IllegalStateException("Не удалось загрузить лоты аукциона", e);
        } finally { synchronized (pages) { rowFlights.remove(key, flight); } }
    }

    private record LoadedMenu(MenuData data, long expiresAt) {}

    private LoadedMenu loadMenuDataUncached(AuctionCurrency currency, String playerName, int maxDays, int page, int pageSize, AuctionSort sort, String category, String sellerFilter, String searchFilter, AuctionViewType viewType) {
        long cutoff = System.currentTimeMillis() - maxDays * 86_400_000L;
        MenuQueryResult result = sharedRows(currency, playerName, maxDays, page, pageSize, sort, category, sellerFilter, searchFilter, viewType, cutoff);
        try (Connection conn = open()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            List<AuctionItem> items = result.items();
            int totalCount = result.totalCount();

            int sellingCount = 0, expiredCount = 0;
            if (viewType == AuctionViewType.MAIN) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(CASE WHEN status=? THEN 1 END) as selling, " + "COUNT(CASE WHEN status=? AND created_at<? THEN 1 END) as expired " + "FROM auction_items WHERE currency=? AND seller_name=?")) {
                    ps.setInt(1, active);
                    ps.setInt(2, active);
                    ps.setLong(3, cutoff);
                    ps.setString(4, currency.name());
                    ps.setString(5, playerName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            sellingCount = rs.getInt("selling");
                            expiredCount = rs.getInt("expired");
                        }
                    }
                }
            }
            MenuData data = new MenuData(items, totalCount, sellingCount, expiredCount, loadProfiles(conn, items, playerName));
            conn.commit();
            return new LoadedMenu(data, result.validUntil());
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось загрузить страницу аукциона", e);
        }
    }


    public PlayerProfile getPlayerProfile(String playerName) {
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(p.discord,'' ) AS discord,COALESCE(p.store_name,'' ) AS store_name,(SELECT COUNT(*) FROM sales_history WHERE seller_name=?) AS sales FROM (VALUES(1)) x LEFT JOIN auction_player_profiles p ON p.player_name=?")) {
            ps.setString(1, playerName);
            ps.setString(2, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerProfile(playerName, rs.getString("discord"), rs.getString("store_name"), rs.getInt("sales"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось загрузить профиль игрока " + playerName, e);
        }
        return PlayerProfile.empty(playerName);
    }

    public void savePlayerProfile(String playerName, String discord, String storeName) {
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("MERGE INTO auction_player_profiles(player_name,discord,store_name,discord_plain,store_name_plain) KEY(player_name) VALUES(?,?,?,?,?)")) {
            ps.setString(1, playerName);
            ps.setString(2, discord);
            ps.setString(3, storeName);
            ps.setString(4, TextUtils.colorize(discord));
            ps.setString(5, TextUtils.colorize(storeName));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось сохранить профиль игрока " + playerName, e);
        }
    }

    public Optional<String> findProfileOwner(String value) {
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement("SELECT player_name FROM auction_player_profiles WHERE LOWER(discord_plain)=? OR LOWER(store_name_plain)=? LIMIT 1")) {
            String plain = TextUtils.colorize(value).toLowerCase();
            ps.setString(1, plain);
            ps.setString(2, plain);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка поиска профиля " + value, e);
        }
        return Optional.empty();
    }

    private Map<String, PlayerProfile> loadProfiles(Connection conn, List<AuctionItem> items, String playerName) throws SQLException {
        Map<String, PlayerProfile> profiles = new HashMap<>();

        for (AuctionItem item : items) {
            String name = item.sellerName();
            profiles.putIfAbsent(name, PlayerProfile.empty(name));
        }
        profiles.putIfAbsent(playerName, PlayerProfile.empty(playerName));

        if (profiles.isEmpty()) return profiles;

        StringBuilder sql = new StringBuilder("SELECT player_name,discord,store_name FROM auction_player_profiles WHERE player_name IN (");
        for (int i = 0; i < profiles.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int index = 1;
            for (String name : profiles.keySet()) {
                ps.setString(index++, name);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("player_name");
                    PlayerProfile profile = profiles.get(name);
                    profiles.put(name, new PlayerProfile(name, rs.getString("discord"), rs.getString("store_name"), profile.sales()));
                }
            }
        }

        StringBuilder salesSql = new StringBuilder("SELECT seller_name,COUNT(*) AS sales FROM sales_history WHERE seller_name IN (");
        for (int i = 0; i < profiles.size(); i++) {
            if (i > 0) salesSql.append(',');
            salesSql.append('?');
        }
        salesSql.append(") GROUP BY seller_name");

        try (PreparedStatement ps = conn.prepareStatement(salesSql.toString())) {
            int index = 1;
            for (String name : profiles.keySet()) {
                ps.setString(index++, name);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("seller_name");
                    PlayerProfile profile = profiles.get(name);
                    profiles.put(name, new PlayerProfile(name, profile.discord(), profile.storeName(), rs.getInt("sales")));
                }
            }
        }
        return profiles;
    }

    private record MenuQueryResult(List<AuctionItem> items, int totalCount, long validUntil) {}

    private MenuQueryResult findPageAndCount(Connection conn, AuctionCurrency currency, int page, int pageSize, AuctionSort sort, String category, String sellerFilter, String searchFilter, AuctionViewType viewType, long cutoff, String playerName) throws SQLException {
        List<Object> params = new ArrayList<>();
        params.add(currency.name());
        params.add(active);

        StringBuilder baseWhere = new StringBuilder("FROM auction_items WHERE currency=? AND status=?");

        if (viewType == AuctionViewType.MAIN) {
            baseWhere.append(" AND created_at >= ?");
            params.add(cutoff);
            if (TextUtils.isNotBlank(category) && !category.equalsIgnoreCase("all")) {
                baseWhere.append(" AND material=?");
                params.add(category.toUpperCase());
            }
            if (TextUtils.isNotBlank(sellerFilter)) {
                baseWhere.append(" AND seller_name LIKE ?");
                params.add("%" + sellerFilter + "%");
            }
            if (TextUtils.isNotBlank(searchFilter)) {
                String pattern = "%" + searchFilter + "%";
                List<String> materials = ru.moscow.foxkiss.utils.ItemUtils.getSearchIndex().findMaterials(searchFilter);
                baseWhere.append(" AND (");
                if (!materials.isEmpty()) {
                    baseWhere.append("material IN (");
                    for (int i = 0; i < materials.size(); i++) {
                        if (i > 0) baseWhere.append(',');
                        baseWhere.append('?');
                        params.add(materials.get(i));
                    }
                    baseWhere.append(") OR ");
                }
                baseWhere.append("material LIKE ? OR seller_name LIKE ? OR seller_name IN (SELECT player_name FROM auction_player_profiles WHERE discord_plain LIKE ? OR store_name_plain LIKE ?))");
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }
        } else if (viewType == AuctionViewType.SELLING) {
            baseWhere.append(" AND seller_name = ?");
            params.add(playerName);
        } else if (viewType == AuctionViewType.EXPIRED) {
            baseWhere.append(" AND seller_name = ?");
            params.add(playerName);
            baseWhere.append(" AND created_at < ?");
            params.add(cutoff);
        }

        String columns = "id,seller_name,currency,item,price,created_at,status,material,amount,price_per_item,lot_type,operation_id,processing_started_at,NULL AS inventory_data";
        StringBuilder sql = new StringBuilder("SELECT " + columns + (separatePageCount ? " " : ", COUNT(*) OVER() AS total_count "));
        sql.append(baseWhere);

        String orderBy = switch (sort) {
            case NEWEST -> "created_at DESC";
            case OLDEST -> "created_at ASC";
            case EXPENSIVE -> "price DESC";
            case CHEAP -> "price ASC";
            case EXPENSIVE_PER_ITEM -> "price_per_item DESC, id DESC";
            case CHEAP_PER_ITEM -> "price_per_item ASC, id ASC";
        };
        sql.append(" ORDER BY ").append(orderBy);
        sql.append(" LIMIT ? OFFSET ?");

        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, pageSize);
        params.add(safePageSize);
        params.add(safePage * safePageSize);

        ObjectList<AuctionItem> items = new ObjectArrayList<>();
        int totalCount = 0;
        if (separatePageCount) {
            try (PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) " + baseWhere)) {
                for (int i = 0; i < params.size() - 2; i++) count.setObject(i + 1, params.get(i));
                try (ResultSet rs = count.executeQuery()) { rs.next(); totalCount = rs.getInt(1); }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (first && !separatePageCount) {
                        try {
                            totalCount = rs.getInt("total_count");
                        } catch (SQLException ignored) {}
                        first = false;
                    }
                    items.add(readItem(rs));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (items.isEmpty() && !separatePageCount) {
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) ").append(baseWhere);
            List<Object> countParams = new ArrayList<>(params.subList(0, params.size() - 2));
            try (PreparedStatement ps = conn.prepareStatement(countSql.toString())) {
                for (int i = 0; i < countParams.size(); i++) {
                    ps.setObject(i + 1, countParams.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getInt(1);
                    }
                }
            }
        }

        return new MenuQueryResult(items, totalCount, Long.MAX_VALUE);
    }

    private Connection open() throws SQLException { return sqlRuntime.open(dataSource); }
}
