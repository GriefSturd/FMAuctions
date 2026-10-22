package ru.moscow.foxkiss.commands;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionMenu;
import ru.moscow.foxkiss.utils.CommandUtils;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class AuctionCommand implements CommandExecutor, TabCompleter {

    private final IConfigManager cfg;
    private final AuctionCurrency cur;
    private final AuctionMenu menu;
    private final AuctionService service;

    private final Object2ObjectOpenHashMap<String, String> toMat = new Object2ObjectOpenHashMap<>();

    public AuctionCommand(IConfigManager configManager, AuctionCurrency currency, AuctionMenu auctionMenu, AuctionService auctionService) {
        this.cfg = configManager;
        this.cur = currency;
        this.menu = auctionMenu;
        this.service = auctionService;
        updateCache();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Команда доступна только для игроков.");
            return true;
        }

        if (args.length == 0) {
            menu.openMain(p, cur, 0, null, null, null, null);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "sell" -> { return handleSell(p, args); }
            case "sellinv" -> { return handleSellInventory(p, args); }
            case "search" -> { return handleSearch(p, args); }
            case "items" -> {
                service.takePendingInventoryItems(p, cur);
                return true;
            }
            default -> {
                menu.openSeller(p, cur, args[0]);
                return true;
            }
        }
    }

    private boolean handleSell(Player p, String[] args) {
        Double price = CommandUtils.parsePrice(p, args, cfg);
        if (price == null) return true;
        service.sell(p, cur, price);
        return true;
    }

    private boolean handleSellInventory(Player p, String[] args) {
        Double price = CommandUtils.parsePrice(p, args, cfg);
        if (price == null) return true;
        service.sellInventory(p, cur, price);
        return true;
    }

    private boolean handleSearch(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(PlaceholderUtils.applypapi(p, cfg.getConfigValues().messages().enterPlayerName(), cfg));
            return true;
        }

        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (query.isEmpty()) {
            p.sendMessage(PlaceholderUtils.applypapi(p, cfg.getConfigValues().messages().enterPlayerName(), cfg));
            return true;
        }

        Material mat = null;
        String matName = toMat.get(query.toLowerCase());
        if (matName != null) {
            mat = Material.getMaterial(matName);
        }

        if (mat == null) {
            mat = Material.matchMaterial(query.toUpperCase());
        }

        if (mat == null) {
            menu.openMain(p, cur, 0, null, null, query, "all");
            return true;
        }

        menu.openMain(p, cur, 0, null, null, mat.name(), "all");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        if (!sender.hasPermission("fmauction.admin")) {
            ObjectList<String> completions = new ObjectArrayList<>();
            completions.add("items");
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
            return CommandUtils.getResult(args, completions);
        }

        final ObjectList<String> completions = new ObjectArrayList<>();
        if (args.length == 1) {
            completions.add("sell");
            completions.add("sellinv");
            completions.add("search");
            completions.add("items");
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
            return CommandUtils.getResult(args, completions);
        }
        return Collections.emptyList();
    }

    public void updateCache() {
        Set<String> all = ItemUtils.getAllTranslatedMaterials();
        toMat.clear();

        for (String matName : all) {
            Material m = Material.getMaterial(matName);
            if (m == null) continue;
            String trans = ItemUtils.getTranslation(m);
            if (trans != null && !trans.isEmpty()) {
                toMat.put(trans.toLowerCase(), matName);
            }
        }
    }

    public void clearCache() {
        updateCache();
    }
}
