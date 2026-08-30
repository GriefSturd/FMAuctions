package ru.moscow.foxkiss.auction.services;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.services.base.BaseAuctionService;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.PriceFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class AuctionSellInventoryService extends BaseAuctionService {

    private final LimitService limitService;
    private final AuctionValidationService validationService;

    public AuctionSellInventoryService(SchedulerService scheduler, IConfigManager configManager, AuctionRepository repository, LimitService limitService, AuctionValidationService validationService, AuctionTransactionService transactionService) {
        super(scheduler, configManager, repository, transactionService);
        this.limitService = limitService;
        this.validationService = validationService;
    }

    public void sellInventory(Player player, AuctionCurrency currency, double price) {
        if (!validationService.isEconomyAvailable(currency)) {
            sendMessage(player, config().messages().economyUnavailable());
            return;
        }

        ConfigValues.InventorySellingConfig inv = config().inventorySelling();
        String symbol = currency.symbol(config());
        boolean enabled = currency == AuctionCurrency.VAULT ? inv.moneyAuc() : inv.rublesAuc();
        if (!enabled) {
            sendMessage(player, config().messages().economyUnavailable());
            return;
        }

        if (!validationService.isValidPrice(price)) {
            sendMessage(player, config().messages().noPrice());
            return;
        }

        boolean isDonate = currency == AuctionCurrency.PLAYER_POINTS;
        if (!validationService.isPriceInRange(price, currency, isDonate)) {
            double min = validationService.getMinPrice(currency, isDonate);
            double max = validationService.getMaxPrice(currency, isDonate);
            String msg;
            if (price < min) {
                msg = config().messages().priceTooLow().replace("{min_price}", String.valueOf((long) min)).replace("{symbol_value}", symbol);
            } else {
                msg = config().messages().priceTooHigh().replace("{max_price}", String.valueOf((long) max)).replace("{symbol_value}", symbol);
            }
            sendMessage(player, msg);
            return;
        }

        List<ItemStack> contents = collectInventory(player);
        int itemCount = 0;
        for (ItemStack it : contents) {
            if (it != null && it.getType() != Material.AIR) itemCount++;
        }

        if (itemCount == 0) {
            sendMessage(player, config().messages().air());
            return;
        }

        if (itemCount > inv.maxItems()) {
            sendMessage(player, config().messages().limitReached());
            return;
        }

        if (itemCount < inv.minItems()) {
            sendMessage(player, config().messages().inventoryMinItems().replace("{min_items}", String.valueOf(inv.minItems())));
            return;
        }

        if (price < inv.minPrice()) {
            sendMessage(player, config().messages().priceTooLow()
                    .replace("{min_price}", String.valueOf(inv.minPrice()))
                    .replace("{symbol_value}", symbol));
            return;
        }
        if (price > inv.maxPrice()) {
            sendMessage(player, config().messages().priceTooHigh()
                    .replace("{max_price}", String.valueOf(inv.maxPrice()))
                    .replace("{symbol_value}", symbol));
            return;
        }

        int limit = limitService.getLimit(player, currency);
        String playerName = player.getName();
        List<Material> shulkerMaterials = inv.shulkerMaterials();
        Material displayMaterial = (shulkerMaterials == null || shulkerMaterials.isEmpty())
                ? Material.WHITE_SHULKER_BOX
                : shulkerMaterials.get(new Random().nextInt(shulkerMaterials.size()));
        ItemStack displayItem = new ItemStack(displayMaterial, 1);
        List<ItemStack> finalContents = contents;

        double commission = chargeCommission(player, currency, config().commission().sellInventory(), price);
        if (commission == -1) return;

        scheduler.runAsync(() -> {
            int active = repository.countActiveBySellerSince(playerName, currency, 0);
            if (active >= limit) {
                scheduler.runSync(() -> {
                    if (commission > 0) transactionService.depositMoney(player, currency, commission);
                    sendMessage(player, config().messages().limitReached());
                });
                return;
            }

            long id = repository.createInventory(playerName, currency, displayItem, finalContents, price);
            if (id <= 0) {
                scheduler.runSync(() -> {
                    if (commission > 0) transactionService.depositMoney(player, currency, commission);
                    sendMessage(player, config().messages().databaseError());
                });
                return;
            }

            scheduler.runSync(() -> {
                player.getInventory().clear();
                String formatted = PriceFormatter.format(price) + symbol;
                String msg = config().messages().sellSuccess().replace("{symbol_value}", formatted);
                player.sendMessage(PlaceholderUtils.applypapi(player, msg, configManager));
            });
        });
    }

    private List<ItemStack> collectInventory(Player player) {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            contents.add(item == null ? null : item.clone());
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            contents.add(item == null ? null : item.clone());
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        contents.add(offHand == null ? null : offHand.clone());
        return contents;
    }
}
