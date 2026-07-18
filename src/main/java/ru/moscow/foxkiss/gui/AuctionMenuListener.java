package ru.moscow.foxkiss.gui;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.moscow.foxkiss.FMAuction;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.ArrayList;
import java.util.List;

public final class AuctionMenuListener implements Listener {

    private final IConfigManager configManager;
    private final AuctionMenu auctionMenu;
    private final AuctionService auctionService;
    private final FMAuction plugin;
    private final NamespacedKey actionKey;

    public AuctionMenuListener(IConfigManager configManager, AuctionMenu auctionMenu, AuctionService auctionService, FMAuction plugin) {
        this.configManager = configManager;
        this.auctionMenu = auctionMenu;
        this.auctionService = auctionService;
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "action");
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AuctionMenuHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (holder.viewType() == AuctionViewType.QUANTITY) {
            handleQuantity(player, holder, slot);
            return;
        }

        if (holder.viewType() == AuctionViewType.CONFIRM) {
            handleConfirm(player, holder, slot);
            return;
        }

        Long lotId = holder.getLot(slot);
        if (lotId != null) {
            handleLotClick(player, holder, slot, lotId, event.isRightClick());
            return;
        }

        handleButtonClick(player, holder, slot, event.isRightClick());
    }

    private void handleQuantity(Player player, AuctionMenuHolder holder, int slot) {
        ConfigValues.QuantityMenuConfig qm = configManager.getConfigValues().guiConfig().quantityMenu();

        if (slot == qm.slotAmount()) {
            auctionService.buy(player, holder.lotId(), holder.selectedAmount());
            player.closeInventory();
            return;
        }

        int amount = holder.selectedAmount();
        if (slot == qm.slotDecrease10()) amount -= 10;
        else if (slot == qm.slotDecrease1()) amount--;
        else if (slot == qm.slotIncrease1()) amount++;
        else if (slot == qm.slotIncrease10()) amount += 10;
        else return;

        if (amount < 1) amount = 1;
        if (amount > holder.maxAmount()) {
            String message = configManager.getConfigValues().messages().get("quantity-exceeded")
                    .replace("{max}", String.valueOf(holder.maxAmount()));
            message = PlaceholderUtils.setPlaceholders(player, message);
            player.sendMessage(TextUtils.colorize(message));
            amount = holder.maxAmount();
        }

        holder.selectedAmount(amount);

        AuctionItem auctionItem = holder.getAuctionItem();
        if (auctionItem != null && holder.getInventory() != null) {
            auctionMenu.updateQuantityDisplay(holder.getInventory(), holder, auctionItem);
        }
    }

    private void handleConfirm(Player player, AuctionMenuHolder holder, int slot) {
        ItemStack clicked = holder.getInventory().getItem(slot);
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String actionName = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (actionName == null) return;
        ActionType action = ActionType.get(actionName);
        if (action == null) return;

        switch (action) {
            case CONFIRM:
                auctionService.buy(player, holder.confirmLotId(), holder.confirmAmount());
                player.closeInventory();
                break;
            case CANCEL:
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    private void handleLotClick(Player player, AuctionMenuHolder holder, int slot, long lotId, boolean rightClick) {
        if (holder.viewType() == AuctionViewType.SELLING || holder.viewType() == AuctionViewType.EXPIRED) {
            auctionService.take(player, lotId);
            player.closeInventory();
            return;
        }

        Integer amount = holder.getLotAmount(slot);
        if (amount == null || amount <= 0) return;

        if (rightClick && amount > 1) {
            auctionMenu.openQuantityAsync(player, holder.currency(), lotId);
            return;
        }

        if (configManager.getConfigValues().confirmMenu().enabled() && !rightClick) {
            auctionMenu.openConfirm(player, holder.currency(), lotId, amount);
        } else {
            auctionService.buy(player, lotId, amount);
            player.closeInventory();
        }
    }

    private void handleButtonClick(Player player, AuctionMenuHolder holder, int slot, boolean rightClick) {
        ItemStack clicked = holder.getInventory().getItem(slot);
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String actionName = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (actionName == null) return;
        ActionType action = ActionType.get(actionName);
        if (action == null) return;

        int currentPage = holder.page();
        int totalPages = holder.totalPages();

        switch (action) {
            case MAIN:
                auctionMenu.openMain(player, holder.currency(), 0, null, null, null, null);
                break;
            case SELLING:
                auctionMenu.openSelling(player, holder.currency(), 0);
                break;
            case EXPIRED:
                auctionMenu.openExpired(player, holder.currency(), 0);
                break;
            case PREVIOUS:
                if (currentPage > 0) {
                    auctionMenu.openInventory(player, holder.viewType(), holder.currency(), currentPage - 1,
                            holder.sort(), holder.sellerFilter(), holder.searchFilter(), holder.category());
                }
                break;
            case NEXT:
                if (currentPage + 1 < totalPages) {
                    auctionMenu.openInventory(player, holder.viewType(), holder.currency(), currentPage + 1,
                            holder.sort(), holder.sellerFilter(), holder.searchFilter(), holder.category());
                }
                break;
            case REFRESH:
                auctionMenu.openInventory(player, holder.viewType(), holder.currency(), currentPage,
                        holder.sort(), holder.sellerFilter(), holder.searchFilter(), holder.category());
                break;
            case SORT:
                AuctionSort sort = rightClick ? holder.sort().previous() : holder.sort().next();
                auctionMenu.openMain(player, holder.currency(), 0, sort, holder.sellerFilter(), holder.searchFilter(), holder.category());
                break;
            case CATEGORIES:
                String currentCat = holder.category();
                String newCat = rightClick ? getPreviousCategory(currentCat) : getNextCategory(currentCat);
                auctionMenu.openMain(player, holder.currency(), 0, holder.sort(), holder.sellerFilter(), holder.searchFilter(), newCat);
                break;
            default:
                break;
        }
    }

    private List<String> getCategoriesList() {
        List<String> cats = new ArrayList<>(configManager.getConfigValues().categories().keySet());
        if (!cats.contains("all")) cats.add(0, "all");
        return cats;
    }

    private String getNextCategory(String current) {
        List<String> cats = getCategoriesList();
        if (cats.isEmpty()) return "all";
        if (current == null || current.isEmpty()) return cats.get(0);
        int index = cats.indexOf(current.toLowerCase());
        if (index < 0) return cats.get(0);
        return cats.get((index + 1) % cats.size());
    }

    private String getPreviousCategory(String current) {
        List<String> cats = getCategoriesList();
        if (cats.isEmpty()) return "all";
        if (current == null || current.isEmpty()) return cats.get(cats.size() - 1);
        int index = cats.indexOf(current.toLowerCase());
        if (index < 0) return cats.get(cats.size() - 1);
        return cats.get((index - 1 + cats.size()) % cats.size());
    }
}