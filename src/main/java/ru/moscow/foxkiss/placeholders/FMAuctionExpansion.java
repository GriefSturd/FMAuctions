package ru.moscow.foxkiss.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.economy.VaultChatApi;
import ru.moscow.foxkiss.economy.VaultPermissionApi;
import ru.moscow.foxkiss.utils.CacheManager;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.*;
import java.util.logging.Level;

public final class FMAuctionExpansion extends PlaceholderExpansion {

    private static final AuctionCurrency auctionCurrency = AuctionCurrency.VAULT;

    private final JavaPlugin plugin;
    private final AuctionRepository repository;
    private final VaultChatApi vaultChat;
    private final VaultPermissionApi vaultPerm;
    private BukkitTask cacheTask;

    private volatile List<TopPlayerInfo> cachedTop = Collections.emptyList();
    private final Map<UUID, CachedPlayerStats> statsCache = new HashMap<>();

    public FMAuctionExpansion(JavaPlugin plugin, AuctionRepository repository, CacheManager cacheManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.vaultChat = new VaultChatApi();
        this.vaultPerm = new VaultPermissionApi();

        cacheManager.registerClearTask(() -> {
            statsCache.clear();
            cachedTop = Collections.emptyList();
        });

        scheduleCacheUpdate();
        updateCacheAsync();
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
        String[] parts = params.toLowerCase(Locale.ROOT).split("_");

        if (parts.length == 3
                && "my".equals(parts[0])
                && "top".equals(parts[1])) {

            if (player == null) return "0";

            String field = parts[2];

            if (!field.equals("sold") && !field.equals("money")) {
                return null;
            }

            return getPlayerStats(player, field);
        }

        if (parts.length != 4 || !"top".equals(parts[0]) || !"money".equals(parts[1])) {
            return null;
        }

        int position;
        try {
            position = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (position < 1 || position > 5) {
            return null;
        }

        String field = parts[3];
        if (!field.equals("nick") && !field.equals("sold") && !field.equals("prefix") && !field.equals("money")) {
            return null;
        }

        List<TopPlayerInfo> top = cachedTop;
        if (position > top.size()) {
            return switch (field) {
                case "nick", "prefix" -> "&7[&c-&7]";
                case "sold" -> "0";
                case "money" -> "0";
                default -> null;
            };
        }

        TopPlayerInfo info = top.get(position - 1);
        return switch (field) {
            case "nick" -> info.name();
            case "sold" -> String.valueOf(info.soldCount());
            case "money" -> formatMoney(info.money());
            case "prefix" -> info.prefix();
            default -> null;
        };
    }

    private String getPlayerStats(OfflinePlayer player, String field) {
        String name = player.getName();
        if (name == null) return "0";
        CachedPlayerStats cached = statsCache.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        if (cached != null && (now - cached.timestamp()) < 60_000L) {
            return field.equals("sold") ? String.valueOf(cached.sold()) : formatMoney(cached.money());
        }

        repository.getPlayerStats(name, auctionCurrency).thenAccept(stats -> {
            statsCache.put(player.getUniqueId(), new CachedPlayerStats(stats.soldCount(), stats.totalEarned(), System.currentTimeMillis()));
        });

        if (cached != null) {
            return field.equals("sold") ? String.valueOf(cached.sold()) : formatMoney(cached.money());
        }
        return "0";
    }

    private void scheduleCacheUpdate() {
        cacheTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateCacheAsync, 1200L, 6000L);
    }

    public void disable() {
        if (cacheTask != null) {
            cacheTask.cancel();
            cacheTask = null;
        }
    }

    private String formatMoney(double amount) {
        return PriceFormatter.format(amount);
    }

    private void updateCacheAsync() {
        repository.getTopSellers(auctionCurrency, 1000)
                .thenApply(list -> {
                    list.sort((a, b) -> Double.compare(b.totalEarned(), a.totalEarned()));
                    return list.size() > 5 ? list.subList(0, 5) : list;
                })
                .thenApply(list -> {
                    List<TopPlayerInfo> result = new ArrayList<>(list.size());
                    for (AuctionRepository.TopSeller seller : list) {
                        String prefix = getPrefixForPlayer(seller.name());
                        result.add(new TopPlayerInfo(seller.name(), seller.soldCount(), seller.totalEarned(), prefix));
                    }
                    return result;
                })
                .thenAccept(result -> cachedTop = Collections.unmodifiableList(result))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Не удалось обновить топ кэш аукциона", ex);
                    return null;
                });
    }

    private String getPrefixForPlayer(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            String group = vaultPerm.getPrimaryGroup(online);
            return vaultChat.getGroupPrefix(online.getWorld(), group);
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name != null && name.equalsIgnoreCase(playerName)) {
                String group = vaultPerm.getPrimaryGroup(offline);
                return vaultChat.getGroupPrefix(null, group);
            }
        }
        return "";
    }

    private record TopPlayerInfo(String name, int soldCount, double money, String prefix) { }
    private record CachedPlayerStats(int sold, double money, long timestamp) { }
}