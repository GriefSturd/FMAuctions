package ru.moscow.foxkiss.permissions;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;

import java.util.Map;

public final class LimitService {

    private final IConfigManager configManager;
    private Permission permission;

    public LimitService(IConfigManager configManager) {
        this.configManager = configManager;
    }

    public void init() {
        RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager().getRegistration(Permission.class);
        if (registration != null) {
            permission = registration.getProvider();
        }
    }

    public int getLimit(Player player, AuctionCurrency currency) {
        Map<String, Integer> groupLimits = currency == AuctionCurrency.VAULT
                ? configManager.getConfigValues().vaultGroupLimits()
                : configManager.getConfigValues().playerPointsGroupLimits();

        int best = groupLimits.getOrDefault("default", 1);
        if (permission == null) {
            return best;
        }

        for (Map.Entry<String, Integer> entry : groupLimits.entrySet()) {
            String group = entry.getKey();
            if ("default".equalsIgnoreCase(group) || permission.playerInGroup(player, group)) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best;
    }
}
