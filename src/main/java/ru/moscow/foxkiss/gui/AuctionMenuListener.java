package ru.moscow.foxkiss.gui;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.*;

public final class AuctionMenuListener implements Listener {

    private final IConfigManager configManager;
    private final AuctionMenu auctionMenu;
    private final AuctionService auctionService;
    private final JavaPlugin plugin;
    private final NamespacedKey actionKey;

    private List<String> cats;
    private final Map<String, Long> quantityMessageCooldowns = new HashMap<>();
    private final Map<UUID, Long> updateCooldowns = new HashMap<>();
    private final Map<UUID, Long> takeCooldowns = new HashMap<>();
    private final Map<UUID, Integer> cooldownMessageSkips = new HashMap<>();

    public AuctionMenuListener(IConfigManager configManager, AuctionMenu auctionMenu, AuctionService auctionService, JavaPlugin plugin) {
        this.configManager = configManager;
        this.auctionMenu = auctionMenu;
        this.auctionService = auctionService;
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "action");
        reloadCategories();
    }

    public void reloadCategories() {
        cats = new ArrayList<>(configManager.getConfigValues().categories().keySet());
        if (!cats.contains("all")) cats.add(0, "all");
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AuctionMenuHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;
        Long lotId = holder.getLot(slot);
        if (lotId != null) {
            handleLotClick(player, holder, slot, lotId, event.isRightClick());
            return;
        }
        if (holder.viewType() == AuctionViewType.QUANTITY && slot == configManager.getConfigValues().guiConfig().quantityMenu().slotAmount()) {
            boolean success = auctionService.buy(player, holder.lotId(), holder.selectedAmount());
            if (success) {
                auctionMenu.openMain(player, holder.currency(), 0, null, null, null, null);
            }
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String actionName = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (actionName == null) return;
        ActionType action = ActionType.get(actionName);
        if (action == null) return;
        switch (holder.viewType()) {
            case QUANTITY -> handleQuantityAction(player, holder, action, slot);
            case CONFIRM -> handleConfirmAction(player, holder, action);
            default -> handleNavigationAction(player, holder, action, event.isRightClick());
        }
    }

    private void handleQuantityAction(Player player, AuctionMenuHolder holder, ActionType action, int slot) {
        int amount = holder.selectedAmount();
        switch (action) {
            case DECREASE_10 -> amount -= 10;
            case DECREASE_1 -> amount--;
            case INCREASE_1 -> amount++;
            case INCREASE_10 -> amount += 10;
            default -> { return; }
        }
        if (amount < 1) amount = 1;
        if (amount > holder.maxAmount()) {
            String nick = player.getName();
            long now = System.currentTimeMillis();
            long last = quantityMessageCooldowns.getOrDefault(nick, 0L);
            if (now - last >= 5000L) {
                player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().quantityExceeded()
                        .replace("{max}", String.valueOf(holder.maxAmount())), configManager));
                quantityMessageCooldowns.put(nick, now);
            }
            amount = holder.maxAmount();
        }
        holder.selectedAmount(amount);
        AuctionItem auctionItem = holder.getAuctionItem();
        if (auctionItem != null && holder.getInventory() != null) {
            auctionMenu.updateQuantityDisplay(holder.getInventory(), holder, auctionItem);
        }
    }

    private void handleConfirmAction(Player player, AuctionMenuHolder holder, ActionType action) {
        switch (action) {
            case CONFIRM -> {
                boolean success = auctionService.buy(player, holder.confirmLotId(), holder.confirmAmount());
                if (success) {
                    auctionMenu.openMain(player, holder.currency(), 0, null, null, null, null);
                }
            }
            case CANCEL -> player.closeInventory();
            default -> {}
        }
    }

    private void handleNavigationAction(Player player, AuctionMenuHolder holder, ActionType action, boolean rightClick) {
        int currentPage = holder.page();
        int totalPages = holder.totalPages();
        switch (action) {
            case MAIN -> auctionMenu.openMain(player, holder.currency(), 0, null, null, null, null);
            case SELLING -> auctionMenu.openSelling(player, holder.currency(), 0);
            case EXPIRED -> auctionMenu.openExpired(player, holder.currency(), 0);
            case PREVIOUS -> {
                if (currentPage > 0) {
                    auctionMenu.openInventory(player, holder.viewType(), holder.currency(), currentPage - 1,
                            holder.sort(), holder.sellerFilter(), holder.searchFilter(), holder.category());
                }
            }
            case NEXT -> {
                if (currentPage + 1 < totalPages) {
                    auctionMenu.openInventory(player, holder.viewType(), holder.currency(), currentPage + 1,
                            holder.sort(), holder.sellerFilter(), holder.searchFilter(), holder.category());
                }
            }
            case REFRESH -> {
                if (tryUseCooldown(player, updateCooldowns,
                        configManager.getConfigValues().cooldowns().updateAuctionSeconds())) {
                    auctionMenu.refreshInventory(player, holder);
                }
            }
            case SORT -> {
                AuctionSort sort = rightClick ? holder.sort().previous() : holder.sort().next();
                auctionMenu.openMain(player, holder.currency(), 0, sort, holder.sellerFilter(), holder.searchFilter(), holder.category());
            }
            case CATEGORIES -> {
                String currentCat = holder.category();
                String newCat = rightClick ? getPreviousCategory(currentCat) : getNextCategory(currentCat);
                auctionMenu.openMain(player, holder.currency(), 0, holder.sort(), holder.sellerFilter(), holder.searchFilter(), newCat);
            }
            default -> {}
        }
    }

    private void handleLotClick(Player player, AuctionMenuHolder holder, int slot, long lotId, boolean rightClick) {
        if (holder.viewType() == AuctionViewType.SELLING || holder.viewType() == AuctionViewType.EXPIRED) {
            if (!tryUseCooldown(player, takeCooldowns,
                    configManager.getConfigValues().cooldowns().takeItemSeconds())) {
                return;
            }
            boolean taken = auctionService.take(player, lotId);
            if (taken) {
                plugin.getServer().getScheduler().runTask(plugin, () -> auctionMenu.refreshInventory(player, holder));
            }
            return;
        }
        Integer amount = holder.getLotAmount(slot);
        if (amount == null || amount <= 0) return;
        if (rightClick && amount > 1) {
            auctionMenu.openQuantity(player, holder.currency(), lotId);
            return;
        }
        if (configManager.getConfigValues().confirmMenu().enabled() && !rightClick) {
            auctionMenu.openConfirm(player, holder.currency(), lotId, amount);
        } else {
            boolean success = auctionService.buy(player, lotId, amount);
            if (success) {
                auctionMenu.refreshInventory(player, holder);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        updateCooldowns.remove(uuid);
        takeCooldowns.remove(uuid);
        cooldownMessageSkips.remove(uuid);
        quantityMessageCooldowns.remove(event.getPlayer().getName());
        auctionMenu.removeRefreshProgress(uuid);
    }

    private boolean tryUseCooldown(Player player, Map<UUID, Long> cooldowns, double seconds) {
        ConfigValues.Cooldowns settings = configManager.getConfigValues().cooldowns();
        if (!settings.cooldownEnabled()) return true;
        long cooldownMillis = Math.round(seconds * 1000D);
        if (cooldownMillis <= 0L) return true;
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long lastUse = cooldowns.getOrDefault(playerId, 0L);
        if (now - lastUse >= cooldownMillis) {
            cooldowns.put(playerId, now);
            cooldownMessageSkips.remove(playerId);
            return true;
        }
        int skips = cooldownMessageSkips.getOrDefault(playerId, 0);
        if (settings.cooldownMessageEnabled() && skips == 0) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().cooldownItem(), configManager));
            cooldownMessageSkips.put(playerId, 2);
        } else if (settings.cooldownMessageEnabled()) {
            cooldownMessageSkips.put(playerId, skips - 1);
        }
        return false;
    }

    private List<String> getCategoriesList() { return cats; }

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