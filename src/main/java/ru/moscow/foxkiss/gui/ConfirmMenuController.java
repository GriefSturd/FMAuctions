package ru.moscow.foxkiss.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.builder.MenuBuilder;
import ru.moscow.foxkiss.utils.TextUtils;

public final class ConfirmMenuController {

    private final IConfigManager configManager;
    private final ItemDisplayFactory itemFactory;
    private final MenuBuilder menuBuilder;

    public ConfirmMenuController(IConfigManager configManager, ItemDisplayFactory itemFactory, MenuBuilder menuBuilder) {
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        this.menuBuilder = menuBuilder;
    }

    public void openConfirm(Player player, AuctionCurrency currency, AuctionItem item, int amount) {
        ConfigValues.ConfirmMenuConfig config = configManager.getConfigValues().confirmMenu();
        int finalAmount = (amount == Integer.MAX_VALUE) ? item.amount() : amount;

        AuctionMenuHolder holder = AuctionMenuHolder.builder()
                .viewType(AuctionViewType.CONFIRM)
                .currency(currency)
                .viewer(player.getUniqueId())
                .lotId(item.id())
                .selectedAmount(1)
                .totalPages(1)
                .maxAmount(item.amount())
                .auctionItem(item)
                .confirmAmount(finalAmount)
                .confirmLotId(item.id())
                .build();

        Inventory inv = Bukkit.createInventory(holder, config.size(), TextUtils.component(configManager.getConfigValues().guiConfig().titles().confirmBuy()));
        holder.setInventory(inv);

        menuBuilder.fillGlass(inv, config.glassPanes());

        inv.setItem(config.itemSlot(), itemFactory.createBuyItem(item, finalAmount));

        setConfirmButton(inv, config.confirm());
        setConfirmButton(inv, config.cancel());

        player.openInventory(inv);
    }

    private void setConfirmButton(Inventory inv, ConfigValues.ConfirmButtonConfig config) {
        for (int slot : config.slots()) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, itemFactory.createButton(config));
            }
        }
    }
}