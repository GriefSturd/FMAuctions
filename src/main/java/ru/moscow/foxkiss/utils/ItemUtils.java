package ru.moscow.foxkiss.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public final class ItemUtils {

    private ItemUtils() {
    }

    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.displayName(componentWithoutItalic(name));
            meta.lore(lore.stream().map(ItemUtils::componentWithoutItalic).toList());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    public static ItemStack skull(String base64, String name, List<String> lore) {
        ItemStack itemStack = named(Material.PLAYER_HEAD, name, lore);
        if (base64 == null || base64.isBlank()) {
            return itemStack;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = org.bukkit.Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64));
            skullMeta.setPlayerProfile(profile);
            itemStack.setItemMeta(skullMeta);
        }
        return itemStack;
    }

    public static boolean isSellable(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() != Material.AIR && itemStack.getAmount() > 0;
    }

    private static Component componentWithoutItalic(String text) {
        return TextUtils.component(text).decoration(TextDecoration.ITALIC, false);
    }
}

