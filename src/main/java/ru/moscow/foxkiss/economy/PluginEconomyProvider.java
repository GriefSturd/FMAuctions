package ru.moscow.foxkiss.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public final class PluginEconomyProvider implements EconomyProvider {

    private final Map<AuctionCurrency, BiPredicate<Player, Double>> hasMap = new EnumMap<>(AuctionCurrency.class);
    private final Map<AuctionCurrency, BiFunction<Player, Double, Boolean>> withdrawMap = new EnumMap<>(AuctionCurrency.class);
    private final Map<AuctionCurrency, BiConsumer<OfflinePlayer, Double>> depositMap = new EnumMap<>(AuctionCurrency.class);

    public void init(JavaPlugin plugin) {
        try {
            VaultApi vault = new VaultApi();
            hasMap.put(AuctionCurrency.VAULT, vault::has);
            withdrawMap.put(AuctionCurrency.VAULT, vault::withdraw);
            depositMap.put(AuctionCurrency.VAULT, vault::deposit);
        } catch (Exception e) {
            plugin.getLogger().warning("Vault недоступен: " + e.getMessage());
        }

        try {
            PlayerPointsApi points = new PlayerPointsApi();
            hasMap.put(AuctionCurrency.PLAYER_POINTS, (p, a) -> points.has(p, (int) Math.ceil(a)));
            withdrawMap.put(AuctionCurrency.PLAYER_POINTS, (p, a) -> points.withdraw(p, (int) Math.ceil(a)));
            depositMap.put(AuctionCurrency.PLAYER_POINTS, (p, a) -> points.deposit(p, (int) Math.ceil(a)));
        } catch (Exception e) {
            plugin.getLogger().warning("PlayerPoints недоступен: " + e.getMessage());
        }
    }

    @Override
    public boolean available(AuctionCurrency currency) {
        return hasMap.containsKey(currency);
    }

    @Override
    public boolean has(Player player, AuctionCurrency currency, double amount) {
        BiPredicate<Player, Double> pred = hasMap.get(currency);
        return pred != null && pred.test(player, amount);
    }

    @Override
    public boolean withdraw(Player player, AuctionCurrency currency, double amount) {
        BiFunction<Player, Double, Boolean> func = withdrawMap.get(currency);
        return func != null && func.apply(player, amount);
    }

    @Override
    public void deposit(OfflinePlayer player, AuctionCurrency currency, double amount) {
        BiConsumer<OfflinePlayer, Double> cons = depositMap.get(currency);
        if (cons != null) {
            cons.accept(player, amount);
        }
    }
}