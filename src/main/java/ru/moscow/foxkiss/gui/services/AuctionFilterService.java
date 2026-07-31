package ru.moscow.foxkiss.gui.services;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.moscow.foxkiss.auction.AuctionItem;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.gui.AuctionViewType;

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

        for (AuctionItem item : items) {
            if (!matchesView(player, viewType, item, maxDays)) continue;
            if (!matchesSeller(sellerFilter, item)) continue;
            if (!matchesSearch(searchFilter, item)) continue;
            if (!matchesCategory(category, item)) continue;
            result.add(item);
        }
        return result;
    }

    private boolean matchesView(Player player, AuctionViewType viewType, AuctionItem item, int maxDays) {
        boolean expired = item.expired(maxDays);

        if (viewType == AuctionViewType.SELLING) {
            return item.sellerName().equalsIgnoreCase(player.getName()) && !expired;
        }
        if (viewType == AuctionViewType.EXPIRED) {
            return item.sellerName().equalsIgnoreCase(player.getName()) && expired;
        }

        return !expired;
    }


    private boolean matchesSeller(String sellerFilter, AuctionItem item) {
        if (sellerFilter == null || sellerFilter.isEmpty()) return true;
        return item.sellerName().equalsIgnoreCase(sellerFilter);
    }

    private boolean matchesSearch(String searchFilter, AuctionItem item) {
        if (searchFilter == null || searchFilter.isEmpty()) return true;
        String normalizedQuery = searchFilter.toLowerCase().replace("_", "").replace(" ", "");
        String normalizedName = item.material().name().toLowerCase().replace("_", "").replace(" ", "");
        return normalizedName.contains(normalizedQuery);
    }

    private boolean matchesCategory(String category, AuctionItem item) {
        if (category == null || category.isEmpty() || category.equalsIgnoreCase("all")) return true;
        Set<Material> materials = configManager.getConfigValues().categories().get(category.toLowerCase());
        return materials.contains(item.material());
    }
}
