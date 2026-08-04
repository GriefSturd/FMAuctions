package ru.moscow.foxkiss.config.loaders;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class MaterialParser {

    private static final String BASEHEAD_PREFIX = "basehead-";

    public record ParsedMaterial(Material material, String skullTexture) {}

    public static ParsedMaterial parse(ConfigurationSection section) {
        String raw;

        if (section.isString("material")) {
            raw = section.getString("material");
        } else {
            raw = section.getString("item");
        }

        if (raw.startsWith(BASEHEAD_PREFIX)) {
            return new ParsedMaterial(Material.PLAYER_HEAD, raw.substring(BASEHEAD_PREFIX.length()));
        }

        Material material = Material.matchMaterial(raw);

        return new ParsedMaterial(material, null);
    }
}