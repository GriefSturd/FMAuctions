package ru.moscow.foxkiss.config.loaders;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class MaterialParser {

    public record ParsedMaterial(Material material, String skullTexture) {}

    public static ParsedMaterial parse(ConfigurationSection section) {
        String raw = section.getString("material");
        if (raw == null) {
            raw = section.getString("item");
        }
        if (raw == null) {
            return new ParsedMaterial(null, null);
        }
        if (raw.startsWith("basehead-")) {
            return new ParsedMaterial(Material.PLAYER_HEAD, raw.substring("basehead-".length()));
        }
        Material material = Material.matchMaterial(raw);
        return new ParsedMaterial(material, null);
    }
}