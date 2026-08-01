package ru.moscow.foxkiss.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;

public final class PluginEconomyProvider implements EconomyProvider {

    private final Map<AuctionCurrency, EconomyHandler> handlers = new EnumMap<>(AuctionCurrency.class);

    public void init(JavaPlugin plugin) {
        try {
            VaultApi vault = new VaultApi();
            handlers.put(AuctionCurrency.VAULT, new EconomyHandler(vault::has, vault:пше :withdraw, vault::deposit));
        } catch (Exception e) {
            plugin.getLogger().warning("Vault недоступен: " + e.getMessage());
        }

        try {
            PlayerPointsApi points = new PlayerPointsApi();
            handlers.put(AuctionCurrency.PLAYER_POINTS, new EconomyHandler(
                    (p, a) -> points.has(p, (int) Math.ceil(a)),
                    (p, a) -> points.withdraw(p, (int) Math.ceil(a)),
                    (p, a) -> points.deposit(p, (int) Math.ceil(a))
            ));
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
        EconomyHandler handler = handlers.get(currency);
        if (handler == null) return false;
        return handler.has.test(player, amount);
    }

    @Override
    public boolean withdraw(Player player, AuctionCurrency currency, double amount) {
        EconomyHandler handler = handlers.get(currency);
        if (handler == null) return false;
        return handler.withdraw.apply(player, amount);
    }

    @Override
    public void deposit(OfflinePlayer player, AuctionCurrency currency, double amount) {
        EconomyHandler handler = handlers.get(currency);
        if (handler != null) {
            handler.deposit.accept(player, amount);
        }
    }

    private record EconomyHandler(
            BiPredicate<Player, Double> has,
            BiFunction<Player, Double, Boolean> withdraw,
            BiConsumer<OfflinePlayer, Double> deposit
    ) {}
}