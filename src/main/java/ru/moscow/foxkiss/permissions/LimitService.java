package ru.moscow.foxkiss.permissions;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LimitService implements Listener {

    private final IConfigManager configManager;
    private Permission permission;
    private final Map<UUID, EnumMap<AuctionCurrency, Integer>> limitCache = new HashMap<>();

    public LimitService(IConfigManager configManager) {
        this.configManager = configManager;
    }

    public void init() {
        RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager().getRegistration(Permission.class);
        if (registration != null) {
            permission = registration.getProvider();
        }
        limitCache.clear();
    }

    public int getLimit(Player player, AuctionCurrency currency) {
        EnumMap<AuctionCurrency, Integer> playerLimits = limitCache.get(player.getUniqueId());
        if (playerLimits != null) {
            Integer cached = playerLimits.get(currency);
            if (cached != null) return cached;
        }

        Map<String, Integer> groupLimits = currency == AuctionCurrency.VAULT
                ? configManager.getConfigValues().vaultGroupLimits()
                : configManager.getConfigValues().playerPointsGroupLimits();

        int best = groupLimits.getOrDefault("default", 1);
        if (permission != null) {
            for (Map.Entry<String, Integer> entry : groupLimits.entrySet()) {
                String group = entry.getKey();
                if ("default".equalsIgnoreCase(group) || permission.playerInGroup(player, group)) {
                    best = Math.max(best, entry.getValue());
                }
            }
        }

        limitCache.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(AuctionCurrency.class))
                .put(currency, best);
        return best;
    }

    public void clearCache() {
        limitCache.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        limitCache.remove(event.getPlayer().getUniqueId());
    }
}