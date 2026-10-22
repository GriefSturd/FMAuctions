package ru.moscow.foxkiss.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionSort;
import ru.moscow.foxkiss.auction.PlayerProfile;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.config.ProfileMenuConfig;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.holders.MainMenuHolder;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ProfileMenu(IConfigManager configManager, AuctionRepository repository, ItemDisplayFactory itemFactory, SchedulerService scheduler) {

    public void open(Player player, AuctionCurrency currency) {
        ProfileMenuConfig config = configManager.getConfigValues().profileMenu();
        if (!config.enabled()) return;

        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> {
            PlayerProfile profile = repository.getPlayerProfile(player.getName());
            scheduler.runSync(() -> {
                if (!player.isOnline()) return;

                MainMenuHolder holder = new MainMenuHolder(AuctionViewType.PROFILE, currency, uuid, 0, AuctionSort.NEWEST, null, null, null);
                String title = TextUtils.colorize(config.title());
                Inventory inventory = Bukkit.createInventory(holder, config.size(), title);
                holder.setInventory(inventory);
                for (ConfigValues.ButtonConfig item : config.items()) {
                    for (int i = 0; i < item.slots().size(); i++) {
                        int slot = item.slots().get(i);
                        inventory.setItem(slot, itemFactory.createButton(replace(item, config, profile)));
                        holder.addActions(slot, item.actions());
                    }
                }
                player.openInventory(inventory);
            });
        });
    }

    private ConfigValues.ButtonConfig replace(ConfigValues.ButtonConfig item, ProfileMenuConfig config, PlayerProfile profile) {
        String discord = profile.discord().isEmpty() ? config.emptyValue() : profile.discord();
        String store = profile.storeName().isEmpty() ? config.emptyValue() : profile.storeName();
        String sales = String.valueOf(profile.sales());
        List<String> lore = new ArrayList<>();
        for (String line : item.lore()) {
            lore.add(line.replace("{discord}", discord).replace("{store}", store).replace("{sales}", sales));
        }
        return new ConfigValues.ButtonConfig(item.material(), item.name().replace("{discord}", discord).replace("{store}", store).replace("{sales}", sales), lore,
                item.skullTexture(), item.actions(), item.slots(), item.customModelData());
    }
}
