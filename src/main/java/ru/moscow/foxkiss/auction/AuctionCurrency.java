package ru.moscow.foxkiss.auction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.moscow.foxkiss.config.ConfigValues;

import java.util.function.Function;

@Getter
@RequiredArgsConstructor
public enum AuctionCurrency {
    VAULT("vault", ConfigValues::symbolVault),
    PLAYER_POINTS("playerpoints", ConfigValues::symbolPlayerPoints);

    private final String configKey;
    private final Function<ConfigValues, String> symbolExtractor;

    public String symbol(ConfigValues values) {
        return symbolExtractor.apply(values);
    }
}