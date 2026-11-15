package ru.moscow.foxkiss.gui.menu;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionMenu;
import ru.moscow.foxkiss.gui.actions.Action;
import ru.moscow.foxkiss.gui.enums.ActionType;
import ru.moscow.foxkiss.gui.holders.ConfirmMenuHolder;
import ru.moscow.foxkiss.gui.holders.InventoryMenuHolder;
import ru.moscow.foxkiss.gui.holders.QuantityMenuHolder;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.List;
import java.util.UUID;

public final class AuctionPurchaseActions {
    private final IConfigManager configManager;
    private final AuctionMenu auctionMenu;
    private final AuctionService auctionService;
    private final Object2LongMap<UUID> quantityMessageCooldowns = new Object2LongOpenHashMap<>();

    public AuctionPurchaseActions(IConfigManager configManager, AuctionMenu auctionMenu, AuctionService auctionService) {
        this.configManager = configManager;
        this.auctionMenu = auctionMenu;
        this.auctionService = auctionService;
    }

    public void onPlayerQuit(UUID uuid) { quantityMessageCooldowns.removeLong(uuid); }

    public void handleQuantityActions(Player player, QuantityMenuHolder holder, int slot, List<Action> actions) {
        boolean buy = false;
        int amount = holder.getSelectedAmount();
        boolean changed = false;
        for (Action action : actions) {
            switch (action.type()) {
                case DECREASE_10 -> { amount -= 10; changed = true; }
                case DECREASE_1 -> { amount--; changed = true; }
                case INCREASE_1 -> { amount++; changed = true; }
                case INCREASE_10 -> { amount += 10; changed = true; }
                case BUY -> { if (slot == configManager.getConfigValues().quantityMenu().slotAmount()) buy = true; }
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
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            long last = quantityMessageCooldowns.getLong(uuid);
            if (now - last >= 5000L) {
                sendMessage(player, configManager.getConfigValues().messages().quantityExceeded()
                        .replace("{max}", String.valueOf(holder.getMaxAmount())));
                quantityMessageCooldowns.put(uuid, now);
            }
            amount = holder.getMaxAmount();
        }

        holder.setSelectedAmount(amount);
        AuctionItem auctionItem = holder.getAuctionItem();
        if (auctionItem != null) {
            auctionMenu.updateQuantityDisplay(holder.getInventory(), holder, auctionItem);
        }
    }

    public void handleConfirmActions(Player player, ConfirmMenuHolder holder, List<Action> actions) {
        boolean confirmed = false;
        for (Action action : actions) {
            if (action.type() == ActionType.CONFIRM) {
                confirmed = true;
                break;
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

    public void handleInventoryActions(Player player, InventoryMenuHolder holder, List<Action> actions) {
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

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(TextUtils.component(PlaceholderUtils.apply(sender, message, configManager)));
    }
}
