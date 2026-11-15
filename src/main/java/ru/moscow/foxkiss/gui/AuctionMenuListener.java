package ru.moscow.foxkiss.gui;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.actions.Action;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.gui.holders.AuctionMenuHolder;
import ru.moscow.foxkiss.gui.holders.ConfirmMenuHolder;
import ru.moscow.foxkiss.gui.holders.InventoryMenuHolder;
import ru.moscow.foxkiss.gui.holders.QuantityMenuHolder;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import ru.moscow.foxkiss.gui.menu.AuctionNavigation;
import ru.moscow.foxkiss.gui.menu.AuctionPurchaseActions;

import java.util.List;
import java.util.UUID;

public final class AuctionMenuListener implements Listener {
    private final IConfigManager configManager;
    private final AuctionMenu auctionMenu;
    private final AuctionService auctionService;

    private final Object2LongOpenHashMap<UUID> updateCooldowns = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<UUID> takeCooldowns = new Object2LongOpenHashMap<>();
    private final Object2IntOpenHashMap<UUID> cooldownMessageSkips = new Object2IntOpenHashMap<>();

    private final AuctionNavigation navigation;
    private final AuctionPurchaseActions purchaseActions;

    public AuctionMenuListener(IConfigManager configManager, AuctionMenu auctionMenu, AuctionService auctionService) {
        this.configManager = configManager;
        this.auctionMenu = auctionMenu;
        this.auctionService = auctionService;
        navigation = new AuctionNavigation(configManager, auctionMenu);
        purchaseActions = new AuctionPurchaseActions(configManager, auctionMenu, auctionService);
    }

    public void reloadCategories() { navigation.reloadCategories(); }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getType() != InventoryType.CHEST) return;
        if (!(inventory.getHolder() instanceof AuctionMenuHolder holder)) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        String materialGroup = holder.getMaterialGroups().isEmpty() ? null : holder.getMaterialGroups().get(slot);
        if (materialGroup != null) {
            auctionMenu.openMaterialOffers(player, holder, materialGroup);
            return;
        }

        Long lotId = holder.getLot(slot);
        if (lotId != null) {
            handleLotClick(player, holder, slot, lotId, event.isRightClick(), event.isShiftClick());
            return;
        }

        List<Action> actions = holder.getActions(slot);
        if (actions == null || actions.isEmpty()) return;

        boolean close = false;
        for (Action action : actions) {
            if (action.type() == ActionType.CLOSE) {
                close = true;
                break;
            }
        }

        switch (holder) {
            case QuantityMenuHolder qh -> purchaseActions.handleQuantityActions(player, qh, slot, actions);
            case ConfirmMenuHolder ch -> purchaseActions.handleConfirmActions(player, ch, actions);
            case InventoryMenuHolder ih -> purchaseActions.handleInventoryActions(player, ih, actions);
            default -> {
                ActionType action = actions.getFirst().type();
                if (action != ActionType.REFRESH || !tryUseCooldown(player, updateCooldowns,
                        configManager.getConfigValues().cooldowns().updateAuctionSeconds())) {
                    navigation.handle(player, holder, action, event.isRightClick());
                }
            }
        }


        if (close && player.getOpenInventory().getTopInventory().getHolder() == holder) {
            player.closeInventory();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AuctionMenuHolder)) return;
        int topSize = top.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleLotClick(Player player, AuctionMenuHolder holder, int slot, long lotId, boolean rightClick, boolean shiftClick) {
        if (holder.isInventoryLot(slot) && holder.getViewType() != AuctionViewType.SELLING && holder.getViewType() != AuctionViewType.EXPIRED) {
            auctionMenu.openInventoryView(player, holder.getCurrency(), lotId);
            return;
        }

        if (holder.getViewType() == AuctionViewType.SELLING || holder.getViewType() == AuctionViewType.EXPIRED) {
            if (tryUseCooldown(player, takeCooldowns, configManager.getConfigValues().cooldowns().takeItemSeconds())) {
                return;
            }

            auctionService.take(player, lotId, success -> {
                if (!success || !player.isOnline()) return;
                auctionMenu.refreshAfterTake(player, holder, slot, lotId);
            });
            return;
        }

        Integer amount = holder.getLotAmount(slot);
        if (amount == null || amount <= 0) return;

        if (shiftClick) {
            auctionService.buy(player, lotId, amount, success -> {
                if (!success || !player.isOnline()) return;
                auctionMenu.refreshInventory(player, holder);
            });
            return;
        }

        if (rightClick && amount > 1) {
            auctionMenu.openQuantity(player, holder.getCurrency(), lotId);
            return;
        }

        if (configManager.getConfigValues().confirmMenu().enabled()) {
            auctionMenu.openConfirm(player, holder.getCurrency(), lotId, amount, false);
            return;
        }

        auctionService.buy(player, lotId, amount, success -> {
            if (!success || !player.isOnline()) return;
            auctionMenu.refreshInventory(player, holder);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        auctionService.transactionService().onPlayerQuit(event.getPlayer());
        UUID uuid = event.getPlayer().getUniqueId();
        updateCooldowns.removeLong(uuid);
        takeCooldowns.removeLong(uuid);
        navigation.onPlayerQuit(uuid);
        purchaseActions.onPlayerQuit(uuid);
        auctionMenu.onPlayerQuit(uuid);
        cooldownMessageSkips.removeInt(uuid);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        auctionService.processPendingDeliveries(event.getPlayer());
        auctionService.processPendingPayouts(event.getPlayer());
    }

    private boolean tryUseCooldown(Player player, Object2LongOpenHashMap<UUID> cooldowns, double seconds) {
        ConfigValues.Cooldowns settings = configManager.getConfigValues().cooldowns();
        if (!settings.cooldownEnabled()) return false;

        long cooldownMillis = Math.round(seconds * 1000D);
        if (cooldownMillis <= 0L) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - cooldowns.getLong(uuid) >= cooldownMillis) {
            cooldowns.put(uuid, now);
            cooldownMessageSkips.removeInt(uuid);
            return false;
        }

        if (!settings.cooldownMessageEnabled()) return true;

        int skips = cooldownMessageSkips.getInt(uuid);

        if (skips > 0) {
            cooldownMessageSkips.put(uuid, skips - 1);
            return true;
        }

        cooldownMessageSkips.put(uuid, 2);

        sendMessage(player, configManager.getConfigValues().messages().cooldownItem());

        return true;
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(TextUtils.component(PlaceholderUtils.apply(sender, message, configManager)));
    }
}
