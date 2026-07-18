package ru.moscow.foxkiss.config;

import org.bukkit.Material;
import ru.moscow.foxkiss.gui.enums.ActionType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ConfigValues(
        String databaseHost,
        int databasePort,
        String databaseUsername,
        String databasePassword,
        String databaseName,
        String prefix,
        Map<String, Integer> vaultGroupLimits,
        Map<String, Integer> vaultPriorityLimits,
        Map<String, Integer> playerPointsGroupLimits,
        Map<String, Integer> playerPointsPriorityLimits,
        int maxAuctionStorageDays,
        int menuSize,
        List<Integer> auctionSlots,
        Map<String, Set<Material>> categories,
        Set<String> allMaterialCategories,
        Map<Integer, GlassPane> sellingGlassPanes,
        Map<Integer, GlassPane> expiredGlassPanes,
        Map<Integer, GlassPane> vaultGlassPanes,
        Map<Integer, GlassPane> playerPointsGlassPanes,
        Map<String, String> messages,
        Map<String, String> sortingNames,
        Map<String, String> categoryNames,
        String symbolVault,
        String symbolPlayerPoints,
        int exitSlot,
        ButtonConfig exitButton,
        GuiConfig guiConfig,
        ConfirmMenuConfig confirmMenu
) {

    public record GuiConfig(
            TitlesConfig titles,
            SortMenuConfig sortMenu,
            ItemLoreConfig itemLore,
            QuantityMenuConfig quantityMenu,
            NavigationConfig navigation,
            CategoryMenuConfig categoryMenu
    ) {}

    public record TitlesConfig(
            String main,
            String selling,
            String expired,
            String quantity
    ) {}

    public record SortMenuConfig(
            Material material,
            String name,
            String selectedPrefix,
            String unselectedPrefix,
            String footer
    ) {}

    public record ItemLoreConfig(
            List<String> lore,
            List<String> loreOne,
            List<String> buyLore
    ) {}

    public record QuantityMenuConfig(
            int slotDecrease10,
            int slotDecrease1,
            int slotAmount,
            int slotIncrease1,
            int slotIncrease10,
            ButtonConfig decrease10,
            ButtonConfig decrease1,
            ButtonConfig amount,
            ButtonConfig increase1,
            ButtonConfig increase10
    ) {}

    public record ButtonConfig(
            Material material,
            String name,
            List<String> lore,
            String skullTexture,
            ActionType action
    ) { }

    public record ConfirmButtonConfig(
            Material material,
            String name,
            List<String> lore,
            String skullTexture,
            int slot,
            ActionType action
    ) {}

    public record NavigationConfig(
            NavigationButton previous,
            NavigationButton refresh,
            NavigationButton next,
            NavigationButton selling,
            NavigationButton expired,
            NavigationButton sort,
            NavigationButton categories
    ) {}

    public record NavigationButton(
            int slot,
            Material material,
            String name,
            List<String> lore,
            String skullTexture,
            ActionType action
    ) {}

    public record CategoryMenuConfig(
            Material material,
            String name,
            String selectedPrefix,
            String unselectedPrefix,
            String footer
    ) {}

    public record GlassPane(
            Material material,
            String displayName
    ) {}

    public record ConfirmMenuConfig(
            boolean enabled,
            int size,
            ConfirmButtonConfig confirm,
            ConfirmButtonConfig cancel,
            Map<Integer, GlassPane> glassPanes
    ) {}
}