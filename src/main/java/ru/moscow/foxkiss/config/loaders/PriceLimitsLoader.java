package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;

public final class PriceLimitsLoader {
    public ConfigValues.PriceLimits load(ConfigurationSection section) {
        return new ConfigValues.PriceLimits(
                section.getDouble("min-price-money-auc"),
                section.getDouble("min-price-money-dauc"),
                section.getDouble("max-price-money-auc"),
                section.getDouble("max-price-money-dauc")
        );
    }
}