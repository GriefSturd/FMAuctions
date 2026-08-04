package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;

public final class SymbolsLoader {
    public record Symbols(String money, String rubles) {}

    public Symbols load(ConfigurationSection section) {
        return new Symbols(
                section.getString("money"),
                section.getString("rubles"));
    }
}