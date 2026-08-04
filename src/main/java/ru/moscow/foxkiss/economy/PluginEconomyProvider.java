package ru.moscow.foxkiss.economy;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;

import java.util.EnumMap;
import java.util.Map;

@RequiredArgsConstructor
public final class PluginEconomyProvider implements EconomyProvider {

    private final Map<AuctionCurrency, CurrencyHandler> handlers = new EnumMap<>(AuctionCurrency.class);

    public void init(JavaPlugin plugin) {
        try {
            handlers.put(AuctionCurrency.VAULT, new VaultHandler());
        } catch (Exception e) {
            plugin.getLogger().warning("Vault недоступен: " + e.getMessage());
        }

        try {
            handlers.put(AuctionCurrency.PLAYER_POINTS, new PlayerPointsHandler());
        } catch (Exception e) {
            plugin.getLogger().warning("PlayerPoints недоступен: " + e.getMessage());
        }
    }

    @Override
    public boolean available(AuctionCurrency currency) {
        return handlers.containsKey(currency);
    }

    @Override
    public boolean has(Player player, AuctionCurrency currency, double amount) {
        CurrencyHandler handler = handlers.get(currency);
        return handler != null && handler.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, AuctionCurrency currency, double amount) {
        CurrencyHandler handler = handlers.get(currency);
        return handler != null && handler.withdraw(player, amount);
    }

    @Override
    public void deposit(OfflinePlayer player, AuctionCurrency currency, double amount) {
        CurrencyHandler handler = handlers.get(currency);
        if (handler != null) handler.deposit(player, amount);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class VaultHandler implements CurrencyHandler {
        private final VaultApi vault = new VaultApi();

        @Override
        public boolean has(Player player, double amount) {
            return vault.has(player, amount);
        }

        @Override
        public boolean withdraw(Player player, double amount) {
            return vault.withdraw(player, amount);
        }

        @Override
        public void deposit(OfflinePlayer player, double amount) {
            vault.deposit(player, amount);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class PlayerPointsHandler implements CurrencyHandler {
        private final PlayerPointsApi points = new PlayerPointsApi();

        @Override
        public boolean has(Player player, double amount) {
            return points.has(player, (int) Math.ceil(amount));
        }

        @Override
        public boolean withdraw(Player player, double amount) {
            return points.withdraw(player, (int) Math.ceil(amount));
        }

        @Override
        public void deposit(OfflinePlayer player, double amount) {
            points.deposit(player, (int) Math.ceil(amount));
        }
    }

    private interface CurrencyHandler {
        boolean has(Player player, double amount);
        boolean withdraw(Player player, double amount);
        void deposit(OfflinePlayer player, double amount);
    }
}