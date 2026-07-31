package ru.moscow.foxkiss.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;

public final class PluginEconomyProvider implements EconomyProvider {

    private VaultApi vaultApi;
    private PlayerPointsApi playerPointsApi;

    public void init(JavaPlugin plugin) {
        try {
            vaultApi = new VaultApi();
        } catch (Exception exception) {
            vaultApi = null;
            plugin.getLogger().warning("Vault economy is unavailable: " + exception.getMessage());
        }

        try {
            playerPointsApi = new PlayerPointsApi();
        } catch (Exception exception) {
            playerPointsApi = null;
            plugin.getLogger().warning("PlayerPoints is unavailable: " + exception.getMessage());
        }
    }

    @Override
    public boolean available(AuctionCurrency currency) {
        return switch (currency) {
            case VAULT -> vaultApi != null;
            case PLAYER_POINTS -> playerPointsApi != null;
        };
    }

    @Override
    public boolean has(Player player, AuctionCurrency currency, double amount) {
        return switch (currency) {
            case VAULT -> vaultApi.has(player, amount);
            case PLAYER_POINTS -> playerPointsApi.has(player, toPointAmount(amount));
        };
    }

    @Override
    public boolean withdraw(Player player, AuctionCurrency currency, double amount) {
        return switch (currency) {
            case VAULT -> vaultApi.withdraw(player, amount);
            case PLAYER_POINTS -> playerPointsApi.withdraw(player, toPointAmount(amount));
        };
    }

    @Override
    public void deposit(OfflinePlayer player, AuctionCurrency currency, double amount) {
        switch (currency) {
            case VAULT -> vaultApi.deposit(player, amount);
            case PLAYER_POINTS -> playerPointsApi.deposit(player, toPointAmount(amount));
        }
    }

    private int toPointAmount(double amount) {
        return (int) Math.ceil(amount);
    }
}
