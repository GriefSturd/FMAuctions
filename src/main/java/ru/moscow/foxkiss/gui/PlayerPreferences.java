package ru.moscow.foxkiss.gui;

import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionSort;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerPreferences {

    private final Map<UUID, Map<AuctionCurrency, AuctionSort>> sorts = new HashMap<>();
    private final Map<UUID, Map<AuctionCurrency, String>> categories = new HashMap<>();

    public AuctionSort getSort(UUID uuid, AuctionCurrency currency) {
        Map<AuctionCurrency, AuctionSort> playerSorts = sorts.get(uuid);

        if (playerSorts == null) {
            return AuctionSort.NEWEST;
        }

        return playerSorts.get(currency);
    }

    public void setSort(UUID uuid, AuctionCurrency currency, AuctionSort sort) {
        sorts.computeIfAbsent(uuid, key -> new HashMap<>())
                .put(currency, sort);
    }

    public String getCategory(UUID uuid, AuctionCurrency currency) {
        Map<AuctionCurrency, String> playerCategories = categories.get(uuid);

        if (playerCategories == null) {
            return "all";
        }

        return playerCategories.get(currency);
    }

    public void setCategory(UUID uuid, AuctionCurrency currency, String category) {
        categories.computeIfAbsent(uuid, key -> new HashMap<>())
                .put(currency, category);
    }
}