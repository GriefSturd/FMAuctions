package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;
import ru.moscow.foxkiss.config.ConfigValues;

public final class CooldownLoader {
    public ConfigValues.Cooldowns load(ConfigurationSection section) {
        return new ConfigValues.Cooldowns(
                Math.max(0.0, section.getDouble("update-auc")),
                Math.max(0.0, section.getDouble("take-item")),
                section.getBoolean("enable-cooldown-message"),
                section.getBoolean("enable-cooldown")
        );
    }
}