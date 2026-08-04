package ru.moscow.foxkiss.placeholders;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.economy.VaultChatApi;
import ru.moscow.foxkiss.economy.VaultPermissionApi;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FMAuctionExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final AuctionRepository repository;
    private final SchedulerService scheduler;

    private final VaultChatApi vaultChat = new VaultChatApi();
    private final VaultPermissionApi vaultPerm = new VaultPermissionApi();

    private List<TopPlayerInfo> cachedTop = new ArrayList<>();
    private final Map<UUID, CachedPlayerStats> statsCache = new ConcurrentHashMap<>();
    private final Set<UUID> loadingStats = ConcurrentHashMap.newKeySet();
    private final Map<String, String> prefixCache = new ConcurrentHashMap<>();

    public FMAuctionExpansion(JavaPlugin plugin, AuctionRepository repository, SchedulerService scheduler) {
        this.plugin = plugin;
        this.repository = repository;
        this.scheduler = scheduler;
        new BukkitRunnable() {
            @Override
            public void run() {
                updateCache();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 6000L);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "fmauction";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "FrostMine";
    }

    @Override
    @NotNull
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] parts = params.toLowerCase().split("_");

        if (parts.length == 3 && "my".equals(parts[0]) && "top".equals(parts[1])) {
            if (player == null) return "0";
            return getMyTop(player, parts[2]);
        }

        if (parts.length == 4 && "top".equals(parts[0]) && "money".equals(parts[1])) {
            return getTop(parts[2], parts[3]);
        }

        return null;
    }

    private String getMyTop(OfflinePlayer player, String field) {
        String name = player.getName();
        if (name == null) return "0";

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        CachedPlayerStats cached = statsCache.get(uuid);
        if (cached != null && (now - cached.timestamp) < 60000) {
            return field.equals("sold") ? String.valueOf(cached.sold) : formatMoney(cached.money);
        }

        if (!loadingStats.contains(uuid)) {
            scheduler.runAsync(() -> loadStatsAsync(uuid, name));
        }

        return "0";
    }

    private void loadStatsAsync(UUID uuid, String name) {
        if (!loadingStats.add(uuid)) return;
        try {
            AuctionRepository.PlayerStats stats = repository.getPlayerStats(name, AuctionCurrency.VAULT);
            statsCache.put(uuid, new CachedPlayerStats(stats.soldCount(), stats.totalEarned(), System.currentTimeMillis()));
        } finally {
            loadingStats.remove(uuid);
        }
    }

    private String getTop(String posStr, String field) {
        int pos;
        try {
            pos = Integer.parseInt(posStr);
        } catch (NumberFormatException e) {
            return null;
        }
        if (pos < 1 || pos > 5) return null;

        List<TopPlayerInfo> top = cachedTop;
        if (pos > top.size()) {
            if (field.equals("nick") || field.equals("prefix")) return "&7[&c-&7]";
            if (field.equals("sold") || field.equals("money")) return "0";
            return null;
        }

        TopPlayerInfo info = top.get(pos - 1);
        switch (field) {
            case "nick": return info.name;
            case "sold": return String.valueOf(info.soldCount);
            case "money": return formatMoney(info.money);
            case "prefix": return info.prefix;
            default: return null;
        }
    }

    public void disable() {
        statsCache.clear();
        prefixCache.clear();
        cachedTop = new ArrayList<>();
    }

    private void updateCache() {
        try {
            List<AuctionRepository.TopSeller> list = repository.getTopSellers(AuctionCurrency.VAULT, 1000);
            list.sort((a, b) -> Double.compare(b.totalEarned(), a.totalEarned()));
            if (list.size() > 5) list = list.subList(0, 5);

            List<TopPlayerInfo> result = new ArrayList<>();
            for (AuctionRepository.TopSeller seller : list) {
                String prefix = prefixCache.get(seller.name());
                if (prefix == null) {
                    prefix = getPrefixForPlayer(seller.name());
                    prefixCache.put(seller.name(), prefix);
                }
                result.add(new TopPlayerInfo(seller.name(), seller.soldCount(), seller.totalEarned(), prefix));
            }
            cachedTop = result;
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось обновить топ: " + e.getMessage());
        }
    }

    private String getPrefixForPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            String group = vaultPerm.getPrimaryGroup(online);
            return vaultChat.getGroupPrefix(online.getWorld(), group);
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                String group = vaultPerm.getPrimaryGroup(offline);
                return vaultChat.getGroupPrefix(null, group);
            }
        }
        return "";
    }

    private String formatMoney(double amount) {
        return PriceFormatter.format(amount);
    }

    private static class TopPlayerInfo {
        final String name;
        final int soldCount;
        final double money;
        final String prefix;
        TopPlayerInfo(String name, int soldCount, double money, String prefix) {
            this.name = name;
            this.soldCount = soldCount;
            this.money = money;
            this.prefix = prefix;
        }
    }

    private static class CachedPlayerStats {
        final int sold;
        final double money;
        final long timestamp;
        CachedPlayerStats(int sold, double money, long timestamp) {
            this.sold = sold;
            this.money = money;
            this.timestamp = timestamp;
        }
    }
}