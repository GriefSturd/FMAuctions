package ru.moscow.foxkiss.gui;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.actions.Action;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.holders.ConfirmMenuHolder;
import ru.moscow.foxkiss.gui.holders.InventoryMenuHolder;
import ru.moscow.foxkiss.gui.holders.QuantityMenuHolder;
import ru.moscow.foxkiss.utils.PlaceholderUtils;

import java.util.*;

@RequiredArgsConstructor
public final class AuctionMenuListener implements Listener {
    private final IConfigManager configManager;
    private final AuctionMenu auctionMenu;
    private final AuctionService auctionService;
    private final int quantitySlot;

    private ObjectList<String> cats;
    private final Object2LongOpenHashMap<String> quantityMessageCooldowns = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<UUID> updateCooldowns = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<UUID> takeCooldowns = new Object2LongOpenHashMap<>();
    private final Object2IntOpenHashMap<UUID> cooldownMessageSkips = new Object2IntOpenHashMap<>();

    public AuctionMenuListener(IConfigManager configManager, AuctionMenu auctionMenu, AuctionService auctionService, JavaPlugin plugin) {
        this.configManager = configManager;
        this.auctionMenu = auctionMenu;
        this.auctionService = auctionService;
        this.quantitySlot = configManager.getConfigValues().quantityMenu().slotAmount();
        reloadCategories();
    }

    public void reloadCategories() {
        cats = new ObjectArrayList<>(configManager.getConfigValues().categories().keySet());
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

        List<Action> actions = holder.getActions(slot);
        if (actions == null || actions.isEmpty()) return;

        boolean close = false;
        for (Action action : actions) {
            if (action.type() == ActionType.CLOSE) {
                close = true;
            }
        }

        if (holder instanceof QuantityMenuHolder qh) {
            handleQuantityActions(player, qh, slot, actions);
        } else if (holder instanceof ConfirmMenuHolder ch) {
            handleConfirmActions(player, ch, actions);
        } else if (holder instanceof InventoryMenuHolder ih) {
            handleInventoryActions(player, ih, actions);
        } else {
            handleNavigationActions(player, holder, actions, event.isRightClick());
        }

        if (close && player.getOpenInventory().getTopInventory().getHolder() == holder) {
            player.closeInventory();
        }
    }

    private void handleQuantityActions(Player player, QuantityMenuHolder holder, int slot, List<Action> actions) {
        boolean buy = false;
        int amount = holder.getSelectedAmount();
        boolean changed = false;
        for (Action action : actions) {
            switch (action.type()) {
                case DECREASE_10 -> { amount -= 10; changed = true; }
                case DECREASE_1 -> { amount--; changed = true; }
                case INCREASE_1 -> { amount++; changed = true; }
                case INCREASE_10 -> { amount += 10; changed = true; }
                case BUY -> { if (slot == quantitySlot) buy = true; }
                default -> { }
            }
        }

        if (buy) {
            auctionService.buy(player, holder.getLotId(), holder.getSelectedAmount(), success -> {
                if (!success || !player.isOnline()) return;
                auctionMenu.openMain(player, holder.getCurrency(), 0, null, null, null, null);
            });
            return;
        }

        if (!changed) return;

        if (amount < 1) amount = 1;

        if (amount > holder.getMaxAmount()) {
            String nick = player.getName();
            long now = System.currentTimeMillis();
            long last = quantityMessageCooldowns.getLong(nick);
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

    private void handleConfirmActions(Player player, ConfirmMenuHolder holder, List<Action> actions) {
        boolean confirmed = false;
        for (Action action : actions) {
            switch (action.type()) {
                case CONFIRM -> confirmed = true;
                default -> { }
            }
        }

        if (confirmed) {
            if (holder.isInventoryBuy()) {
                auctionService.buyInventory(player, holder.getConfirmLotId(), success -> {
                    if (!success || !player.isOnline()) return;
                    auctionMenu.openMain(player, holder.getCurrency(), 0, null, null, null, null);
                });
            } else {
                auctionService.buy(player, holder.getConfirmLotId(), holder.getConfirmAmount(), success -> {
                    if (!success || !player.isOnline()) return;
                    auctionMenu.openMain(player, holder.getCurrency(), 0, null, null, null, null);
                });
            }
        }
    }

    private void handleInventoryActions(Player player, InventoryMenuHolder holder, List<Action> actions) {
        for (Action action : actions) {
            switch (action.type()) {
                case CLOSE -> { }
                case INVENTORY_PREV -> {
                    if (holder.getPrevLotId() != -1L) {
                        auctionMenu.openInventoryView(player, holder.getCurrency(), holder.getPrevLotId());
                    }
                }
                case INVENTORY_NEXT -> {
                    if (holder.getNextLotId() != -1L) {
                        auctionMenu.openInventoryView(player, holder.getCurrency(), holder.getNextLotId());
                    }
                }
                case BUY -> {
                    if (configManager.getConfigValues().confirmMenu().enabled()) {
                        auctionMenu.openConfirm(player, holder.getCurrency(), holder.getLotId(), 1, true);
                    } else {
                        auctionService.buyInventory(player, holder.getLotId(), success -> {
                            if (!success || !player.isOnline()) return;
                            auctionMenu.openMain(player, holder.getCurrency(), 0, null, null, null, null);
                        });
                    }
                }
                default -> { }
            }
        }
    }

    private void handleNavigationActions(Player player, AuctionMenuHolder holder, List<Action> actions, boolean rightClick) {
        ActionType action = actions.get(0).type();

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
        if (holder.isInventoryLot(slot) && holder.getViewType() != AuctionViewType.SELLING && holder.getViewType() != AuctionViewType.EXPIRED) {
            auctionMenu.openInventoryView(player, holder.getCurrency(), lotId);
            return;
        }

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
            auctionMenu.openConfirm(player, holder.getCurrency(), lotId, amount, false);
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

    private boolean tryUseCooldown(Player player, Object2LongOpenHashMap<UUID> cooldowns, double seconds) {
        ConfigValues.Cooldowns settings = configManager.getConfigValues().cooldowns();
        if (!settings.cooldownEnabled()) return true;

        long cooldownMillis = Math.round(seconds * 1000D);
        if (cooldownMillis <= 0L) return true;

        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long lastUse = cooldowns.getLong(playerId);

        if (now - lastUse >= cooldownMillis) {
            cooldowns.put(playerId, now);
            cooldownMessageSkips.remove(playerId);
            return true;
        }

        int skips = cooldownMessageSkips.getInt(playerId);
        if (settings.cooldownMessageEnabled() && skips == 0) {
            player.sendMessage(PlaceholderUtils.applypapi(player, configManager.getConfigValues().messages().cooldownItem(), configManager));
            cooldownMessageSkips.put(playerId, 2);
        } else if (settings.cooldownMessageEnabled()) {
            cooldownMessageSkips.put(playerId, skips - 1);
        }
        return false;
    }

    private ObjectList<String> getCategoriesList() {
        return cats;
    }

    private String getNextCategory(String current) {
        ObjectList<String> categories = getCategoriesList();
        int index = categories.indexOf(current.toLowerCase());
        if (index < 0) return categories.get(0);
        return categories.get((index + 1) % categories.size());
    }

    private String getPreviousCategory(String current) {
        ObjectList<String> categories = getCategoriesList();
        int index = categories.indexOf(current.toLowerCase());
        if (index < 0) return categories.get(categories.size() - 1);
        return categories.get((index - 1 + categories.size()) % categories.size());
    }
}