package ru.moscow.foxkiss.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ItemUtils {

    private static final Map<String, PlayerProfile> profileSkull = new HashMap<>();
    private static final Map<String, ItemStack> cacheSkull = new HashMap<>();
    private static final Map<String, String> perevod = new HashMap<>();

    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.displayName(TextUtils.component(name).decoration(TextDecoration.ITALIC, false));

        if (!lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>(lore.size());
            for (String line : lore) {
                loreComponents.add(TextUtils.component(line).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(loreComponents);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack skull(String base64, String name, List<String> lore) {
        if (base64 == null || base64.isBlank()) {
            return named(Material.PLAYER_HEAD, name, lore);
        }

        String cacheKey = base64 + "|" + name + "|" + (lore != null ? String.join("", lore) : "");
        ItemStack cached = cacheSkull.get(cacheKey);
        if (cached != null) {
            return cached.clone();
        }

        ItemStack item = named(Material.PLAYER_HEAD, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = profileSkull.computeIfAbsent(base64, ItemUtils::createSkullProfile);
            skullMeta.setPlayerProfile(profile);
            item.setItemMeta(skullMeta);
        }

        cacheSkull.put(cacheKey, item.clone());
        return item;
    }

    public static void loadTranslations(File itemsFile) {
        perevod.clear();
        if (itemsFile == null || !itemsFile.exists()) {
            Bukkit.getLogger().warning("items.yml не найден, переводы не загружены.");
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(itemsFile);
        for (String key : cfg.getKeys(false)) {
            if (Material.getMaterial(key) == null) {
                Bukkit.getLogger().warning("Пропущен не поддерживаемый или не валидный материал: " + key);
                continue;
            }
            perevod.put(key, cfg.getString(key));
        }
    }

    public static String getItemDisplayName(ItemStack item) {
        Material material = item.getType();
        String materialName = material.name();
        return perevod.getOrDefault(materialName, materialName.toLowerCase(Locale.ROOT).replace('_', ' '));
    }

    public static boolean isSellable(ItemStack item) {
        return item.getType() != Material.AIR && item.getAmount() > 0;
    }

    private static PlayerProfile createSkullProfile(String texture) {
        UUID uuid = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(uuid);
        profile.setProperty(new ProfileProperty("textures", texture));
        return profile;
    }

    public static void clearCache() {
        cacheSkull.clear();
        profileSkull.clear();
    }

    public static String getTranslation(Material material) {
        if (material == null) return null;
        return perevod.get(material.name());
    }
}