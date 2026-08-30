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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionMenu;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.*;

public final class AuctionCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final IConfigManager cfg;
    private final AuctionCurrency cur;
    private final AuctionMenu menu;
    private final AuctionService service;
    private final AuctionRepository repo;

    private final ObjectList<String> names = new ObjectArrayList<>();
    private final Object2ObjectOpenHashMap<String, String> toMat = new Object2ObjectOpenHashMap<>();
    private final Map<AuctionCurrency, List<String>> matCache = new HashMap<>();
    private final Map<AuctionCurrency, Long> timeCache = new HashMap<>();

    public AuctionCommand(JavaPlugin plugin, IConfigManager configManager, AuctionCurrency currency, AuctionMenu auctionMenu, AuctionService auctionService, AuctionRepository repository) {
        this.plugin = plugin;
        this.cfg = configManager;
        this.cur = currency;
        this.menu = auctionMenu;
        this.service = auctionService;
        this.repo = repository;
        runTask();
    }

    public void runTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateCache();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 6000L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Онли плейерс то юзе команд");
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
            default -> {
                menu.openMain(p, cur, 0, null, null, null, null);
                return true;
            }
        }
    }

    private boolean handleSell(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(PlaceholderUtils.applypapi(p, cfg.getConfigValues().messages().noPrice(), cfg));
            return true;
        }
        double price;
        try { price = Double.parseDouble(args[1]); }
        catch (NumberFormatException e) {
            p.sendMessage(PlaceholderUtils.applypapi(p, cfg.getConfigValues().messages().noPrice(), cfg));
            return true;
        }
        service.sell(p, cur, price);
        return true;
    }

    private boolean handleSellInventory(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(PlaceholderUtils.applypapi(p, cfg.getConfigValues().messages().noPrice(), cfg));
            return true;
        }
        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            p.sendMessage(PlaceholderUtils.applypapi(p, cfg.getConfigValues().messages().noPrice(), cfg));
            return true;
        }
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
        if (matName != null) mat = Material.getMaterial(matName);

        if (mat == null) mat = Material.matchMaterial(query.toUpperCase());

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
            return List.of();
        }

        final ObjectList<String> completions = new ObjectArrayList<>();
        if (args.length == 1) {
            completions.add("sell");
            completions.add("sellinv");
            completions.add("search");
            return getResult(args, completions);
        }
        return Collections.emptyList();
    }

    private ObjectList<String> getResult(String[] args, ObjectList<String> completions) {
        if (completions.isEmpty()) {
            return completions;
        }

        final ObjectList<String> result = new ObjectArrayList<>();
        for (int i = 0; i < completions.size(); i++) {
            String c = completions.get(i);
            if (StringUtil.startsWithIgnoreCase(c, args[args.length - 1])) {
                result.add(c);
            }
        }
        return result;
    }

    private void updateCache() {
        Set<String> all = ItemUtils.getAllTranslatedMaterials();

        names.clear();
        toMat.clear();

        for (String matName : all) {
            Material m = Material.getMaterial(matName);
            if (m == null) continue;
            String trans = ItemUtils.getTranslation(m);
            if (trans != null && !trans.isEmpty()) {
                names.add(trans);
                toMat.put(trans.toLowerCase(), matName);
            }
        }

        matCache.put(cur, repo.getUniqueMaterialNames(cur));
        timeCache.put(cur, System.currentTimeMillis());
    }

    public void clearCache() {
        matCache.clear();
        timeCache.clear();
        names.clear();
        toMat.clear();
    }
}