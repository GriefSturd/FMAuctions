package ru.moscow.foxkiss.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.gui.AuctionMenu;
import ru.moscow.foxkiss.utils.managers.interfaces.IMessageManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AuctionCommand implements CommandExecutor, TabCompleter {

    private static final long CACHE_DURATION = 30_000L;

    private final AuctionCurrency currency;
    private final AuctionMenu auctionMenu;
    private final AuctionService auctionService;
    private final IMessageManager messageManager;
    private final AuctionRepository repository;

    private final Map<AuctionCurrency, List<String>> materialCache = new HashMap<>();
    private final Map<AuctionCurrency, Long> cacheTime = new HashMap<>();

    public AuctionCommand(AuctionCurrency currency, AuctionMenu auctionMenu, AuctionService auctionService, IMessageManager messageManager, AuctionRepository repository) {
        this.currency = currency;
        this.auctionMenu = auctionMenu;
        this.auctionService = auctionService;
        this.messageManager = messageManager;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            auctionMenu.openMain(player, currency, 0, null, null, null, null);
            return true;
        }

        String sub = args[0];

        if ("sell".equalsIgnoreCase(sub)) {
            return handleSell(player, args);
        }

        if ("search".equalsIgnoreCase(sub)) {
            return handleSearch(player, args);
        }

        auctionMenu.openMain(player, currency, 0, null, sub, null, null);
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage(player, "non-price"));
            return true;
        }

        double price;

        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException exception) {
            player.sendMessage(messageManager.getMessage(player, "non-price"));
            return true;
        }

        auctionService.sell(player, currency, price);
        return true;
    }

    private boolean handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage(player, "enter-player-name"));
            return true;
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                builder.append('_');
            }

            builder.append(args[i]);
        }

        String query = builder.toString()
                .toUpperCase(Locale.ROOT);

        Material material = Material.matchMaterial(query);

        if (material == null) {
            player.sendMessage("§cМатериал не найден: " + query);
            return true;
        }

        auctionMenu.openMain(player, currency, 0, null, null, material.name(), "all");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> result = new ArrayList<>(2);

            if ("sell".startsWith(args[0].toLowerCase())) {
                result.add("sell");
            }

            if ("search".startsWith(args[0].toLowerCase())) {
                result.add("search");
            }

            return result;
        }

        if (args.length >= 2 && "search".equalsIgnoreCase(args[0])) {
            updateCache();

            String query = args[1].toUpperCase(Locale.ROOT);

            List<String> materials = materialCache.get(currency);

            if (materials == null) {
                return List.of();
            }

            List<String> result = new ArrayList<>(20);

            for (String material : materials) {
                if (material.contains(query)) {
                    result.add(material);

                    if (result.size() >= 20) {
                        break;
                    }
                }
            }

            return result;
        }

        return List.of();
    }

    private void updateCache() {
        long now = System.currentTimeMillis();

        Long lastUpdate = cacheTime.get(currency);

        if (lastUpdate != null && now - lastUpdate <= CACHE_DURATION) {
            return;
        }

        repository.getUniqueMaterialNames(currency)
                .thenAccept(materials -> {
                    materialCache.put(currency, materials);
                    cacheTime.put(currency, now);
                });
    }
}