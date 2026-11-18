package ru.moscow.foxkiss.commands;

import java.util.Arrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.gui.AuctionMenu;
import ru.moscow.foxkiss.utils.CommandUtils;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.MaterialSearchIndex;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public record AuctionCommand(IConfigManager cfg, AuctionCurrency cur, AuctionMenu menu, AuctionService service) implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sendMessage(sender, cfg.getConfigValues().messages().playerOnly());
            return true;
        }

        if (args.length == 0) {
            menu.openMain(p, cur, 0, null, null, null, null);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "sell" -> handleSell(p, args);
            case "sellinv" -> handleSellInventory(p, args);
            case "sell-opt" -> handleSellWholesale(p, label, args);
            case "search" -> handleSearch(p, args);
            case "items" -> handleItems(p);
            case "active" -> menu.openSelling(p, cur, 0);
            case "expired" -> menu.openExpired(p, cur, 0);
            default -> menu.openSeller(p, cur, args[0]);
        }

        return true;
    }

    private void handleSell(Player p, String[] args) {
        if (args.length < 2) {
            sendMessage(p, cfg.getConfigValues().messages().noPrice());
            return;
        }
        Double price = CommandUtils.parsePrice(p, args[1], cfg);
        if (price == null) return;

        service.sell(p, cur, price);
    }

    private void handleSellInventory(Player p, String[] args) {
        if (args.length < 2) {
            sendMessage(p, cfg.getConfigValues().messages().noPrice());
            return;
        }
        Double price = CommandUtils.parsePrice(p, args[1], cfg);
        if (price == null) return;

        service.sellInventory(p, cur, price);
    }

    private void handleSellWholesale(Player player, String label, String[] args) {
        ConfigValues values = cfg.getConfigValues();
        ConfigValues.WholesaleMessages messages = values.messages().wholesale();
        if (!values.wholesaleEnabled().contains(cur)) {
            sendMessage(player, messages.disabled());
            return;
        }
        if (args.length < 3 || args[1].equals(messages.itemHint())) {
            sendMessage(player, messages.usage().replace("{command}", label));
            return;
        }

        String itemName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        String id = ItemUtils.getSearchIndex().resolveMaterial(itemName, inventoryMaterials(player));
        if (id == null) {
            sendMessage(player, messages.itemNotFound());
            return;
        }

        Double price = CommandUtils.parsePrice(player, args[args.length - 1], cfg);
        if (price != null) service.sellWholesale(player, cur, Material.valueOf(id), price);
    }

    private List<String> completeWholesale(Player player, String[] args) {
        ConfigValues.WholesaleMessages messages = cfg.getConfigValues().messages().wholesale();
        if (args.length == 2 && args[1].isEmpty()) return List.of(messages.itemHint());
        ObjectSet<String> materials = inventoryMaterials(player);
        if (materials.isEmpty()) return List.of();

        MaterialSearchIndex index = ItemUtils.getSearchIndex();
        String last = args[args.length - 1];
        if (args.length > 2) {
            String itemName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
            if (index.resolveMaterial(itemName, materials) != null) {
                return last.isEmpty() ? List.of(messages.priceHint()) : List.of();
            }
        }
        String prefix = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        return index.findInventoryAliases(prefix, prefix.length() - last.length(), materials);
    }

    private static ObjectSet<String> inventoryMaterials(Player player) {
        ObjectSet<String> materials = new ObjectOpenHashSet<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && !item.isEmpty()) materials.add(item.getType().name());
        }
        return materials;
    }

    private void handleItems(Player p) {
        service.takePendingInventoryItems(p, cur);
    }

    private void handleSearch(Player p, String[] args) {
        if (args.length < 2) {
            sendSearchUsage(p);
            return;
        }

        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (query.isEmpty() || query.equals(cfg.getConfigValues().messages().searchHint())) {
            sendSearchUsage(p);
            return;
        }

        menu.openMain(p, cur, 0, null, null, query, "all");
    }

    private void sendSearchUsage(Player p) {
        sendMessage(p, cfg.getConfigValues().messages().enterPlayerName());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("sell-opt")) {
            if (sender instanceof Player player && cfg.getConfigValues().wholesaleEnabled().contains(cur)) {
                return completeWholesale(player, args);
            }
            return List.of();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("search")) {
            String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (query.isBlank()) return List.of(cfg.getConfigValues().messages().searchHint());
            int consumed = query.length() - args[args.length - 1].length();
            List<String> suggestions = new ObjectArrayList<>();
            ObjectSet<String> added = new ObjectOpenHashSet<>();
            for (MaterialSearchIndex.Match match : ItemUtils.getSearchIndex().find(query, 30)) {
                String completion = match.name().substring(consumed);
                if (added.add(completion)) suggestions.add(completion);
            }
            return suggestions;
        }
        if (args.length != 1) {
            return Collections.emptyList();
        }

        ObjectList<String> completions = new ObjectArrayList<>();

        if (sender.hasPermission("fmauction.admin")) {
            completions.add("sell");
            completions.add("sellinv");
        }

        if (sender instanceof Player && cfg.getConfigValues().wholesaleEnabled().contains(cur)) {
            completions.add("sell-opt");
        }
        completions.add("search");
        completions.add("items");
        completions.add("active");
        completions.add("expired");

        for (Player player : Bukkit.getOnlinePlayers()) {
            completions.add(player.getName());
        }

        return CommandUtils.getResult(args, completions);
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(TextUtils.component(PlaceholderUtils.apply(sender, message, cfg)));
    }
}
