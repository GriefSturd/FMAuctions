package ru.moscow.foxkiss.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.AuctionCurrency;

public final class PluginEconomyProvider implements EconomyProvider {

    private VaultApi vaultApi;
    private PlayerPointsApi playerPointsApi;

    public void init() {
        try {
            vaultApi = new VaultApi();
        } catch (Exception ignored) {
            vaultApi = null;
        }

        try {
            playerPointsApi = new PlayerPointsApi();
        } catch (Exception ignored) {
            playerPointsApi = null;
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
            case PLAYER_POINTS -> playerPointsApi.has(player, (int) Math.ceil(amount));
        };
    }

    @Override
    public boolean withdraw(Player player, AuctionCurrency currency, double amount) {
        return switch (currency) {
            case VAULT -> vaultApi.withdraw(player, amount);
            case PLAYER_POINTS -> playerPointsApi.withdraw(player, (int) Math.ceil(amount));
        };
    }

    @Override
    public void deposit(OfflinePlayer player, AuctionCurrency currency, double amount) {
        switch (currency) {
            case VAULT -> vaultApi.deposit(player, amount);
            case PLAYER_POINTS -> playerPointsApi.deposit(player, (int) Math.ceil(amount));
        }
    }
}