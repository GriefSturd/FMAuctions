package ru.moscow.foxkiss.gui;

import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.holders.ConfirmMenuHolder;
import ru.moscow.foxkiss.gui.holders.QuantityMenuHolder;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.*;

@RequiredArgsConstructor
public final class AuctionMenuListener implements Listener {
    private final IConfigManager configManager;
    private final AuctionMenu auctionMenu;
    private final AuctionService auctionService;
    private final NamespacedKey actionKey;
    private final int quantitySlot;

    private List<String> cats;
    private final Map<String, Long> quantityMessageCooldowns = new HashMap<>();
    private final Map<UUID, Long> updateCooldowns = new HashMap<>();
    private final Map<UUID, Long> takeCooldowns = new HashMap<>();
    private final Map<UUID, Integer> cooldownMessageSkips = new HashMap<>();

    public AuctionMenuListener(IConfigManager configManager, AuctionMenu auctionMenu, AuctionService auctionService, JavaPlugin plugin) {
        this.configManager = configManager;
        this.auctionMenu = auctionMenu;
        this.auctionService = auctionService;
        this.actionKey = new NamespacedKey(plugin, "action");
        this.quantitySlot = configManager.getConfigValues().guiConfig().quantityMenu().slotAmount();
        reloadCategories();
    }

    public void reloadCategories() {
        cats = new ArrayList<>(configManager.getConfigValues().categories().keySet());
        if (!cats.contains("all")) {
            cats.add(0, "all");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.CHEST) return;

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

        if (holder instanceof QuantityMenuHolder qh && slot == quantitySlot) {
            auctionService.buy(player, qh.getLotId(), qh.getSelectedAmount(), success -> {
                if (!success || !player.isOnline()) return;
                auctionMenu.openMain(player, qh.getCurrency(), 0, null, null, null, null);
            });
            return;
        }

        ActionType action = holder.getAction(slot);

        if (holder instanceof QuantityMenuHolder qh) {
            handleQuantityAction(player, qh, action);
        } else if (holder instanceof ConfirmMenuHolder ch) {
            handleConfirmAction(player, ch, action);
        } else {
            handleNavigationAction(player, holder, action, event.isRightClick());
        }
    }

    private void handleQuantityAction(Player player, QuantityMenuHolder holder, ActionType action) {
        int amount = holder.getSelectedAmount();
        switch (action) {
            case DECREASE_10 -> amount -= 10;
            case DECREASE_1 -> amount--;
            case INCREASE_1 -> amount++;
            case INCREASE_10 -> amount += 10;
            default -> { return; }
        }

        if (amount < 1) amount = 1;

        if (amount > holder.getMaxAmount()) {
            String nick = player.getName();
            long now = System.currentTimeMillis();
            long last = quantityMessageCooldowns.getOrDefault(nick, 0L);
            if (now - last >= 5000L) {
                player.sendMessage(PlaceholderUtils.applypapi(player,
                        configManager.getConfigValues().messages().quantityExceeded().replace("{max}", String.valueOf(holder.getMaxAmount())),
                        configManager));
                quantityMessageCooldowns.put(nick, now);
            }
            amount = holder.getMaxAmount();
        }

        holder.setSelectedAmount(amount);
        AuctionItem auctionItem = holder.getAuctionItem();
        if (auctionItem != null) {
            auctionMenu.updateQuantityDisplay(holder.getInventory(), holder, auctionItem);
        }
    }

    private void handleConfirmAction(Player player, ConfirmMenuHolder holder, ActionType action) {
        if (action == ActionType.CONFIRM) {
            auctionService.buy(player, holder.getConfirmLotId(), holder.getConfirmAmount(), success -> {
                if (!success || !player.isOnline()) return;
                auctionMenu.openMain(player, holder.getCurrency(), 0, null, null, null, null);
            });
        } else if (action == ActionType.CANCEL) {
            player.closeInventory();
        }
    }

    private void handleNavigationAction(Player player, AuctionMenuHolder holder, ActionType action, boolean rightClick) {
        if (action == ActionType.REFRESH) {
            if (!tryUseCooldown(player, updateCooldowns,
                    configManager.getConfigValues().cooldowns().updateAuctionSeconds())) {
                return;
            }
        }

        int currentPage = holder.getPage();
        int totalPages = holder.getTotalPages();

        switch (action) {
            case MAIN -> auctionMenu.openMain(player, holder.getCurrency(), 0, null, null, null, null);

            case SELLING -> auctionMenu.openSelling(player, holder.getCurrency(), 0);

            case EXPIRED -> auctionMenu.openExpired(player, holder.getCurrency(), 0);

            case PREVIOUS -> {
                if (currentPage <= 0) return;
                holder.setPage(currentPage - 1);
                auctionMenu.refreshInventory(player, holder);
            }
            case NEXT -> {
                if (currentPage + 1 >= totalPages) return;
                holder.setPage(currentPage + 1);
                auctionMenu.refreshInventory(player, holder);
            }
            case REFRESH -> auctionMenu.refreshInventory(player, holder);

            case SORT -> {
                AuctionSort newSort = rightClick ? holder.getSort().previous() : holder.getSort().next();
                holder.setSort(newSort);
                holder.setPage(0);
                auctionMenu.refreshInventory(player, holder);
            }
            case CATEGORIES -> {
                String currentCat = holder.getCategory();
                String newCat = rightClick ? getPreviousCategory(currentCat) : getNextCategory(currentCat);
                holder.setCategory(newCat);
                holder.setPage(0);
                auctionMenu.refreshInventory(player, holder);
            }
            default -> {}
        }
    }

    private void handleLotClick(Player player, AuctionMenuHolder holder, int slot, long lotId, boolean rightClick) {
        if (holder.getViewType() == AuctionViewType.SELLING || holder.getViewType() == AuctionViewType.EXPIRED) {
            if (!tryUseCooldown(player, takeCooldowns, configManager.getConfigValues().cooldowns().takeItemSeconds())) {
                return;
            }

            auctionService.take(player, lotId, success -> {
                if (!success || !player.isOnline()) return;
                auctionMenu.refreshInventory(player, holder);
            });
            return;
        }

        Integer amount = holder.getLotAmount(slot);
        if (amount == null || amount <= 0) return;

        if (rightClick && amount > 1) {
            auctionMenu.openQuantity(player, holder.getCurrency(), lotId);
            return;
        }

        if (configManager.getConfigValues().confirmMenu().enabled() && !rightClick) {
            auctionMenu.openConfirm(player, holder.getCurrency(), lotId, amount);
            return;
        }

        auctionService.buy(player, lotId, amount, success -> {
            if (!success || !player.isOnline()) return;
            auctionMenu.refreshInventory(player, holder);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        updateCooldowns.remove(uuid);
        takeCooldowns.remove(uuid);
        cooldownMessageSkips.remove(uuid);
        quantityMessageCooldowns.remove(event.getPlayer().getName());
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

    private List<String> getCategoriesList() {
        return cats;
    }

    private String getNextCategory(String current) {
        List<String> categories = getCategoriesList();
        int index = categories.indexOf(current.toLowerCase());
        if (index < 0) return categories.get(0);
        return categories.get((index + 1) % categories.size());
    }

    private String getPreviousCategory(String current) {
        List<String> categories = getCategoriesList();
        int index = categories.indexOf(current.toLowerCase());
        if (index < 0) return categories.get(categories.size() - 1);
        return categories.get((index - 1 + categories.size()) % categories.size());
    }
}