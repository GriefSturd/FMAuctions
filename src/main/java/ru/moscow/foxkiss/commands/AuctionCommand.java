
package ru.moscow.foxkiss.commands;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.bukkit.Bukkit;
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
        cfg = configManager;
        cur = currency;
        menu = auctionMenu;
        service = auctionService;
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
            case "sell" -> handleSell(p, args);
            case "sellinv" -> handleSellInventory(p, args);
            case "search" -> handleSearch(p, args);
            case "items" -> service.takePendingInventoryItems(p, cur);
            default -> menu.openSeller(p, cur, args[0]);
        }

        return true;
    }

    private void handleSell(Player p, String[] args) {
        Double price = CommandUtils.parsePrice(p, args, cfg);
        if (price == null) return;

        service.sell(p, cur, price);
    }

    private void handleSellInventory(Player p, String[] args) {
        Double price = CommandUtils.parsePrice(p, args, cfg);
        if (price == null) return;

        service.sellInventory(p, cur, price);
    }

    private void handleSearch(Player p, String[] args) {
        if (args.length < 2) {
            sendSearchUsage(p);
            return;
        }

        String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (query.isEmpty()) {
            sendSearchUsage(p);
            return;
        }

        String matName = toMat.get(query.toLowerCase());
        Material mat = matName != null ? Material.getMaterial(matName) : null;

        if (mat == null) {
            mat = Material.matchMaterial(query.toUpperCase());
        }

        menu.openMain(p, cur, 0, null, null, mat != null ? mat.name() : query, "all");
    }

    private void sendSearchUsage(Player p) {
        p.sendMessage(PlaceholderUtils.applypapi(p, cfg.getConfigValues().messages().enterPlayerName(), cfg));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        ObjectList<String> completions = new ObjectArrayList<>();

        if (sender.hasPermission("fmauction.admin")) {
            completions.add("sell");
            completions.add("sellinv");
            completions.add("search");
        }

        completions.add("items");

        for (Player player : Bukkit.getOnlinePlayers()) {
            completions.add(player.getName());
        }

        return CommandUtils.getResult(args, completions);
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
