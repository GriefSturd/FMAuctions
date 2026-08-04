package ru.moscow.foxkiss.config.loaders;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class MaterialParser {
    private static final String BASEHEAD_PREFIX = "basehead-";

    public record ParsedMaterial(Material material, String skullTexture) {}

    public static ParsedMaterial parse(ConfigurationSection section, boolean fallbackToMaterial) {
        String item = section.getString("item", "");

        if (item.startsWith(BASEHEAD_PREFIX)) {
            return new ParsedMaterial(Material.PLAYER_HEAD, item.substring(BASEHEAD_PREFIX.length()));
        }

        Material material = Material.matchMaterial(item);

        return new ParsedMaterial(material, null);
    }
}