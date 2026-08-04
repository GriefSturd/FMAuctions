package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.moscow.foxkiss.config.ConfigValues;
import ru.moscow.foxkiss.utils.TextUtils;

public final class MessagesLoader {
    private final String prefix;

    public MessagesLoader(String prefix) {
        this.prefix = prefix;
    }

    public ConfigValues.ConfigMessages load(FileConfiguration config) {
        ConfigurationSection messages = config.getConfigurationSection("messages");
        ConfigurationSection admin = messages.getConfigurationSection("admin");
        ConfigurationSection errors = messages.getConfigurationSection("errors");
        ConfigurationSection buy = messages.getConfigurationSection("buy");
        ConfigurationSection expired = messages.getConfigurationSection("expired");
        ConfigurationSection selling = messages.getConfigurationSection("selling");
        ConfigurationSection sell = config.getConfigurationSection("commands.sell");
        ConfigurationSection auctionErrors = config.getConfigurationSection("auction.errors");

        return new ConfigValues.ConfigMessages(
                msg(admin.getString("reload")),
                msg(admin.getString("unknown-subcommand")),
                msg(admin.getString("error-reload")),
                msg(admin.getString("no-permission")),
                msg(errors.getString("no-name")),
                msg(errors.getString("no-id")),
                msg(sell.getString("non-price")),
                msg(errors.getString("no-own")),
                msg(auctionErrors.getString("economy-unavailable")),
                msg(sell.getString("air")),
                msg(sell.getString("success")),
                msg(sell.getString("price-too-low")),
                msg(sell.getString("price-too-high")),
                msg(sell.getString("limit-reached")),
                msg(sell.getString("database-error")),
                msg(String.join("\n", config.getStringList("search.enter-player-name"))),
                msg(buy.getString("buy-seller")),
                msg(buy.getString("otmena")),
                msg(buy.getString("yspex")),
                msg(buy.getString("nomoney")),
                msg(buy.getString("quantity-exceeded")),
                msg(expired.getString("take")),
                msg(selling.getString("take")),
                msg(messages.getString("cooldown")),
                msg(messages.getString("inventory-full")),
                msg(messages.getString("cooldown-item"))
        );
    }

    private String msg(String raw) {
        return raw == null ? "" : TextUtils.colorize(raw.replace("%prefix%", prefix));
    }
}