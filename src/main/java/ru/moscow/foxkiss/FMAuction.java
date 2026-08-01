package ru.moscow.foxkiss;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.auction.H2AuctionRepository;
import ru.moscow.foxkiss.commands.AdminCommand;
import ru.moscow.foxkiss.commands.AuctionCommand;
import ru.moscow.foxkiss.config.impl.ConfigManager;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.economy.PluginEconomyProvider;
import ru.moscow.foxkiss.gui.AuctionMenu;
import ru.moscow.foxkiss.gui.AuctionMenuListener;
import ru.moscow.foxkiss.gui.PlayerPreferences;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.placeholders.FMAuctionExpansion;
import ru.moscow.foxkiss.utils.ItemUtils;
import ru.moscow.foxkiss.utils.managers.MessageManager;
import ru.moscow.foxkiss.utils.managers.interfaces.IMessageManager;

import java.io.File;

public final class FMAuction extends JavaPlugin {

    private IConfigManager configManager;
    private IMessageManager messageManager;
    private AuctionRepository auctionRepository;
    private AuctionService auctionService;
    private AuctionMenu auctionMenu;
    private PluginEconomyProvider economyProvider;
    private LimitService limitService;
    private FMAuctionExpansion placeholderExpansion;
    private PlayerPreferences playerPreferences;
    private AuctionCommand auctionCommand;
    private AuctionCommand donateAuctionCommand;
    private AuctionMenuListener auctionMenuListener;

    private final CacheManager cacheManager = new CacheManager();

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        initializeManager();
        setupMetrics();

        registerCommands();
        registerListeners();
        registerPlaceholders();

        setupCacheTasks();

        long endTime = System.currentTimeMillis();
        getLogger().info("Plugin enabled in " + (endTime - startTime) + " ms");
    }

    @Override
    public void onDisable() {
        if (auctionRepository != null) {
            auctionRepository.close();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.disable();
            placeholderExpansion.unregister();
        }
        cacheManager.clearAll();
    }

    private void setupMetrics() {
        if (!configManager.getConfigValues().bStatsEnabled()) {
            return;
        }
        int pluginId = 33041;
        Metrics metrics = new Metrics(this, pluginId);

        metrics.addCustomChart(new SimplePie("storage_engine", () -> "H2"));
        metrics.addCustomChart(new SimplePie("cooldowns", () -> configManager.getConfigValues().cooldowns().cooldownEnabled() ? "enabled" : "disabled"));
        metrics.addCustomChart(new SimplePie("confirmation_menu", () -> configManager.getConfigValues().confirmMenu().enabled() ? "enabled" : "disabled"));
    }

    public void initializeManager() {
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(configManager.getConfigValues());
        auctionRepository = new H2AuctionRepository(this);
        ItemUtils.loadTranslations(new File(getDataFolder(), "items.yml"));
        auctionRepository.init();
        economyProvider = new PluginEconomyProvider();
        economyProvider.init(this);
        limitService = new LimitService(configManager);
        limitService.init();
        auctionService = new AuctionService(this, configManager, messageManager, auctionRepository, economyProvider, limitService);
        playerPreferences = new PlayerPreferences();
        auctionMenu = new AuctionMenu(this, configManager, auctionRepository, playerPreferences);
    }

    public void reloadAll() {
        ItemUtils.loadTranslations(new File(getDataFolder(), "items.yml"));
        configManager.reload();
        messageManager.reload(configManager.getConfigValues());
        limitService.init();

        cacheManager.clearAll();

        if (auctionMenuListener != null) {
            auctionMenuListener.reloadCategories();
        }
    }

    public IMessageManager getMessageManager() {
        return messageManager;
    }

    private void registerCommands() {
        auctionCommand = new AuctionCommand(this, AuctionCurrency.VAULT, auctionMenu, auctionService, messageManager, auctionRepository);
        donateAuctionCommand = new AuctionCommand(this, AuctionCurrency.PLAYER_POINTS, auctionMenu, auctionService, messageManager, auctionRepository);

        AdminCommand adminCommand = new AdminCommand(this);

        PluginCommand ahCommand = getCommand("ah");
        ahCommand.setExecutor(auctionCommand);
        ahCommand.setTabCompleter(auctionCommand);

        PluginCommand daucCommand = getCommand("dauc");
        daucCommand.setExecutor(donateAuctionCommand);
        daucCommand.setTabCompleter(donateAuctionCommand);

        PluginCommand fmauctionCommand = getCommand("fmauction");
        fmauctionCommand.setExecutor(adminCommand);
        fmauctionCommand.setTabCompleter(adminCommand);
    }

    private void registerListeners() {
        auctionMenuListener = new AuctionMenuListener(configManager, auctionMenu, auctionService, messageManager, this, cacheManager);
        getServer().getPluginManager().registerEvents(auctionMenuListener, this);
        getServer().getPluginManager().registerEvents(limitService, this);
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new FMAuctionExpansion(this, auctionRepository, cacheManager);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI expansion registered successfully.");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion.");
            }
        }
    }

    private void setupCacheTasks() {
        cacheManager.registerClearTask(() -> {
            if (auctionMenu != null) {
                auctionMenu.clearCaches();
            }
            if (limitService != null) {
                limitService.clearCache();
            }
            if (auctionMenuListener != null) {
                auctionMenuListener.reloadCategories();
            }
            if (auctionCommand != null) {
                auctionCommand.clearCache();
            }
            if (donateAuctionCommand != null) {
                donateAuctionCommand.clearCache();
            }
            ItemUtils.clearCache();
        });
    }
}