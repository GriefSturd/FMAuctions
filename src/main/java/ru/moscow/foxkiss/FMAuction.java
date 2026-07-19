package ru.moscow.foxkiss;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionCurrency;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.AuctionService;
import ru.moscow.foxkiss.auction.SQLiteAuctionRepository;
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
import ru.moscow.foxkiss.utils.managers.MessageManager;
import ru.moscow.foxkiss.utils.managers.interfaces.IMessageManager;

public final class FMAuction extends JavaPlugin {

    private IConfigManager configManager;
    private IMessageManager messageManager;
    private AuctionRepository auctionRepository;
    private AuctionService auctionService;
    private AuctionMenu auctionMenu;
    private PluginEconomyProvider economyProvider;
    private LimitService limitService;
    private FMAuctionExpansion placeholderExpansion;
    private ru.moscow.foxkiss.gui.PlayerPreferences playerPreferences;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        initializeManager();

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
            placeholderExpansion.unregister();
        }
    }

    public void initializeManager() {
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(configManager.getConfigValues());
        auctionRepository = new SQLiteAuctionRepository(this);
        auctionRepository.init();
        economyProvider = new PluginEconomyProvider();
        economyProvider.init();
        limitService = new LimitService(configManager);
        limitService.init();
        auctionService = new AuctionService(this, configManager, messageManager, auctionRepository, economyProvider, limitService);
        playerPreferences = new PlayerPreferences();
        auctionMenu = new AuctionMenu(this, configManager, auctionRepository, playerPreferences);
    }

    public void reloadAll() {
        configManager.reload();
        messageManager.reload(configManager.getConfigValues());
        limitService.init();
    }

    public IMessageManager getMessageManager() {
        return messageManager;
    }

    private void registerCommands() {
        AuctionCommand auctionCmd = new AuctionCommand(AuctionCurrency.VAULT, auctionMenu, auctionService, messageManager, auctionRepository);
        AuctionCommand donateAuctionCmd = new AuctionCommand(AuctionCurrency.PLAYER_POINTS, auctionMenu, auctionService, messageManager, auctionRepository);
        AdminCommand adminCommand = new AdminCommand(this);

        PluginCommand ahCommand = getCommand("ah");
        ahCommand.setExecutor(auctionCmd);
        ahCommand.setTabCompleter(auctionCmd);
        
        PluginCommand daucCommand = getCommand("dauc");
        daucCommand.setExecutor(donateAuctionCmd);
        daucCommand.setTabCompleter(donateAuctionCmd);
        
        PluginCommand fmauctionCommand = getCommand("fmauction");
        fmauctionCommand.setExecutor(adminCommand);
        fmauctionCommand.setTabCompleter(adminCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AuctionMenuListener(configManager, auctionMenu, auctionService, this), this);
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new FMAuctionExpansion(this, auctionRepository);
            if (placeholderExpansion.register()) {
                getLogger().info("PlaceholderAPI expansion registered successfully.");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion.");
            }
        }
    }
}
