package ru.moscow.foxkiss.auction;

import ru.moscow.foxkiss.config.ConfigValues;

public enum AuctionCurrency {
    VAULT("vault"),
    PLAYER_POINTS("playerpoints");

    private final String configKey;

    AuctionCurrency(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public String symbol(ConfigValues values) {
        return switch (this) {
            case VAULT -> values.symbolVault();
            case PLAYER_POINTS -> values.symbolPlayerPoints();
        };
    }
}