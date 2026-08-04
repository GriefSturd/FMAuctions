package ru.moscow.foxkiss;

import org.bstats.bukkit.Metrics;
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
import ru.moscow.foxkiss.gui.ItemDisplayFactory;
import ru.moscow.foxkiss.gui.PlayerPreferences;
import ru.moscow.foxkiss.permissions.LimitService;
import ru.moscow.foxkiss.placeholders.FMAuctionExpansion;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.ItemUtils;

import java.io.File;

public final class FMAuction extends JavaPlugin {

    private IConfigManager configManager;
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
    private SchedulerService schedulerService;
    private ItemDisplayFactory itemDisplayFactory;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        initializeManager();
        setupMetrics();

        registerCommands();
        registerListeners();
        registerPlaceholders();

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
        if (schedulerService != null) {
            schedulerService.shutdown();
        }
    }

    private void setupMetrics() {
        if (!configManager.getConfigValues().bStatsEnabled()) {
            return;
        }

        int pluginId = 33068;
        new Metrics(this, pluginId);
    }

    public void initializeManager() {
        configManager = new ConfigManager(this);
        auctionRepository = new H2AuctionRepository(this);
        ItemUtils.loadTranslations(new File(getDataFolder(), "items.yml"));
        auctionRepository.init();

        economyProvider = new PluginEconomyProvider();
        economyProvider.init(this);

        limitService = new LimitService(configManager);
        limitService.init();

        schedulerService = new SchedulerService(this);
        itemDisplayFactory = new ItemDisplayFactory(this, configManager);

        auctionService = AuctionService.create(this, configManager, auctionRepository, economyProvider, limitService, itemDisplayFactory, schedulerService);

        playerPreferences = new PlayerPreferences();
        auctionMenu = new AuctionMenu(this, configManager, auctionRepository, playerPreferences, itemDisplayFactory);
    }

    public void reloadAll() {
        ItemUtils.loadTranslations(new File(getDataFolder(), "items.yml"));
        configManager.reload();
        limitService.init();

        if (auctionMenu != null) {
            auctionMenu.reload();
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
    }

    private void registerCommands() {
        auctionCommand = new AuctionCommand(this, configManager, AuctionCurrency.VAULT, auctionMenu, auctionService, auctionRepository);
        donateAuctionCommand = new AuctionCommand(this, configManager, AuctionCurrency.PLAYER_POINTS, auctionMenu, auctionService, auctionRepository);

        AdminCommand adminCommand = new AdminCommand(this, configManager);

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
        auctionMenuListener = new AuctionMenuListener(configManager, auctionMenu, auctionService, this);
        getServer().getPluginManager().registerEvents(auctionMenuListener, this);
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new FMAuctionExpansion(this, auctionRepository, schedulerService);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI expansion registered successfully.");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion.");
            }
        }
    }
}
