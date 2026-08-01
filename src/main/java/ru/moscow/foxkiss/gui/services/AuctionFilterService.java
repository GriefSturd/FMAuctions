package ru.moscow.foxkiss.gui.services;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionViewType;
import ru.moscow.foxkiss.utils.ItemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AuctionFilterService {

    private final IConfigManager configManager;

    public AuctionFilterService(IConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<AuctionItem> filter(Player player, AuctionViewType viewType, List<AuctionItem> items, String sellerFilter, String searchFilter, String category) {
        List<AuctionItem> result = new ArrayList<>();
        int maxDays = configManager.getConfigValues().maxAuctionStorageDays();
        String query = normalize(searchFilter);

        for (AuctionItem item : items) {
            boolean expired = item.expired(maxDays);

            if (viewType == AuctionViewType.SELLING) {
                if (!item.sellerName().equalsIgnoreCase(player.getName()) || expired) continue;
            } else if (viewType == AuctionViewType.EXPIRED) {
                if (!item.sellerName().equalsIgnoreCase(player.getName()) || !expired) continue;
            } else if (expired) continue;

            if (!sellerFilter.isEmpty() && !item.sellerName().equalsIgnoreCase(sellerFilter)) continue;

            String name = normalize(item.material().name());
            if (!name.contains(query)) {
                String translations = ItemUtils.getTranslation(item.material());
                if (!normalize(translations).contains(query)) continue;
            }

            if (!category.equalsIgnoreCase("all")) {
                Set<Material> mats = configManager.getConfigValues().categories().get(category.toLowerCase());
                if (mats == null || !mats.contains(item.material())) continue;
            }

            result.add(item);
        }
        return result;
    }

    private String normalize(String input) {
        return input.toLowerCase().replace("_", "").replace(" ", "");
    }
}