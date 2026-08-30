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
        ConfigurationSection a = config.getConfigurationSection("messages");
        ConfigurationSection b = a.getConfigurationSection("admin");
        ConfigurationSection c = a.getConfigurationSection("errors");
        ConfigurationSection d = a.getConfigurationSection("buy");
        ConfigurationSection e = a.getConfigurationSection("expired");
        ConfigurationSection f = a.getConfigurationSection("selling");
        ConfigurationSection g = config.getConfigurationSection("commands");
        ConfigurationSection h = g.getConfigurationSection("sell");
        ConfigurationSection i = config.getConfigurationSection("auction");
        ConfigurationSection j = i.getConfigurationSection("errors");

        ConfigurationSection search = config.getConfigurationSection("search");

        return new ConfigValues.ConfigMessages(
                msg(b.getString("reload")),
                msg(b.getString("unknown-subcommand")),
                msg(b.getString("error-reload")),
                msg(b.getString("no-permission")),
                msg(c.getString("no-name")),
                msg(c.getString("no-id")),
                msg(h.getString("non-price")),
                msg(c.getString("no-own")),
                msg(j.getString("economy-unavailable")),
                msg(h.getString("air")),
                msg(h.getString("success")),
                msg(h.getString("price-too-low")),
                msg(h.getString("price-too-high")),
                msg(h.getString("limit-reached")),
                msg(h.getString("database-error")),
                msg(String.join("\n", search.getStringList("enter-player-name"))),
                msg(d.getString("buy-seller")),
                msg(d.getString("otmena")),
                msg(d.getString("yspex")),
                msg(d.getString("nomoney")),
                msg(d.getString("quantity-exceeded")),
                msg(e.getString("take")),
                msg(f.getString("take")),
                msg(a.getString("cooldown")),
                msg(a.getString("inventory-full")),
                msg(a.getString("cooldown-item")),
                msg(h.getString("inventory-min-items")),
                msg(h.getString("commission-charged")),
                msg(h.getString("commission-not-enough"))
        );
    }

    private String msg(String raw) {
        return raw == null ? "" : TextUtils.colorize(raw.replace("%prefix%", prefix));
    }
}
