package ru.moscow.foxkiss.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
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

    public static ItemStack named(Material material, String name, List<String> lore, Integer customModelData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (name != null && !name.isEmpty()) {
            meta.setDisplayName(TextUtils.colorize(name));
        }

        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }

        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                coloredLore.add(TextUtils.colorize(line));
            }
            meta.setLore(coloredLore);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack skull(String base64, String name, List<String> lore, Integer customModelData) {
        if (base64 == null || base64.isBlank()) {
            return named(Material.PLAYER_HEAD, name, lore, customModelData);
        }

        String cacheKey = base64 + "|" + name + "|" + (lore != null ? String.join("", lore) : "") + "|" + customModelData;
        ItemStack cached = cacheSkull.get(cacheKey);
        if (cached != null) {
            return cached.clone();
        }

        ItemStack item = named(Material.PLAYER_HEAD, name, lore, customModelData);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = profileSkull.computeIfAbsent(base64, ItemUtils::createSkullProfile);
            skullMeta.setPlayerProfile(profile);
            item.setItemMeta(skullMeta);
        }

        cacheSkull.put(cacheKey, item.clone());
        return item;
    }

    public static boolean isSellable(ItemStack item) {
        return item.getType() != Material.AIR && item.getAmount() > 0;
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

    public static String getTranslation(Material material) {
        if (material == null) return null;
        return perevod.get(material.name());
    }

    public static Set<String> getAllTranslatedMaterials() {
        return Collections.unmodifiableSet(perevod.keySet());
    }

    public static void clearCache() {
        cacheSkull.clear();
        profileSkull.clear();
    }

    private static PlayerProfile createSkullProfile(String texture) {
        UUID uuid = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(uuid);
        profile.setProperty(new ProfileProperty("textures", texture));
        return profile;
    }
}