package ru.moscow.foxkiss.config.loaders;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class MaterialParser {

    private static final String BASEHEAD_PREFIX = "basehead-";

    public record ParsedMaterial(Material material, String skullTexture) {}


    public static ParsedMaterial parse(
            ConfigurationSection section,
            boolean fallbackToMaterial
    ) {

        String raw;

        // Поддержка material: BARRIER
        if (section.isString("material")) {
            raw = section.getString("material");
        }
        // Поддержка item: STONE / basehead-
        else {
            raw = section.getString("item", "");
        }


        if (raw == null || raw.isBlank()) {

            if (fallbackToMaterial) {
                return new ParsedMaterial(
                        Material.STONE,
                        null
                );
            }

            throw new IllegalArgumentException(
                    "Missing item/material at "
                            + section.getCurrentPath()
            );
        }


        if (raw.startsWith(BASEHEAD_PREFIX)) {

            return new ParsedMaterial(
                    Material.PLAYER_HEAD,
                    raw.substring(BASEHEAD_PREFIX.length())
            );
        }


        Material material =
                Material.matchMaterial(raw);


        if (material == null) {

            throw new IllegalArgumentException(
                    "Unknown material '" +
                            raw +
                            "' at " +
                            section.getCurrentPath()
            );
        }


        return new ParsedMaterial(
                material,
                null
        );
    }
}