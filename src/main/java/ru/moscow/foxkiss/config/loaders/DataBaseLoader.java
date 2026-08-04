package ru.moscow.foxkiss.config.loaders;

import org.bukkit.configuration.ConfigurationSection;

public final class DataBaseLoader {
    public record DataBase(String host, int port, String username, String password, String database) {}

    public DataBase load(ConfigurationSection section) {
        return new DataBase(
                section.getString("host"),
                section.getInt("port"),
                section.getString("username"),
                section.getString("password"),
                section.getString("database")
        );
    }
}