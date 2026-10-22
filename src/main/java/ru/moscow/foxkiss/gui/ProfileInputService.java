package ru.moscow.foxkiss.gui;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.moscow.foxkiss.auction.AuctionRepository;
import ru.moscow.foxkiss.auction.PlayerProfile;
import ru.moscow.foxkiss.config.interfaces.IConfigManager;
import ru.moscow.foxkiss.scheduler.SchedulerService;
import ru.moscow.foxkiss.utils.PlaceholderUtils;
import ru.moscow.foxkiss.utils.TextUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProfileInputService implements Listener {

    private final IConfigManager configManager;
    private final AuctionRepository repository;
    private final SchedulerService scheduler;
    private final JavaPlugin plugin;
    private final Map<UUID, Input> inputs = new HashMap<>();

    public ProfileInputService(JavaPlugin plugin, IConfigManager configManager, AuctionRepository repository, SchedulerService scheduler) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.scheduler = scheduler;
    }

    public void open(Player player, boolean discord) {
        player.closeInventory();

        Location hidden = findSafeLocation(player.getLocation());
        Block block = hidden.getBlock();

        if (!block.getType().isAir()) {
            block.setType(Material.AIR, false);
        }

        BlockState previous = block.getState();
        block.setType(Material.OAK_SIGN, false);
        
        Sign sign = (Sign) block.getState(true);
        org.bukkit.block.sign.SignSide front = sign.getSide(Side.FRONT);
        front.setLine(0, "");
        front.setLine(1, "");
        front.setLine(2, "^^^^^^^^");
        front.setLine(3, "Введите текст");
        sign.update(true);

        inputs.put(player.getUniqueId(), new Input(block, previous, discord));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                try {
                    player.openSign(sign, Side.FRONT);
                } catch (Exception e) {
                    plugin.getLogger().severe("Ошибка при открытии таблички: " + e.getMessage());
                }
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Input remainingInput = inputs.remove(player.getUniqueId());
                if (remainingInput != null) {
                    String message = remainingInput.discord() 
                        ? configManager.getConfigValues().messages().discordCleared()
                        : configManager.getConfigValues().messages().storeCleared();
                    if (player.isOnline()) {
                        player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                    }
                    scheduler.runAsync(() -> repository.savePlayerProfile(player.getName(), "", ""));
                }
                if (block.getType() == Material.OAK_SIGN) {
                    block.setType(Material.AIR, false);
                }
            }, 400L);
        }, 5L);
    }

    private Location findSafeLocation(Location playerLoc) {
        Location safeLoc = playerLoc.clone().add(0, 2, 0);
        
        if (safeLoc.getBlock().getType().isAir()) {
            return safeLoc;
        }
        
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location testLoc = playerLoc.clone().add(x, 2, z);
                if (testLoc.getBlock().getType().isAir()) {
                    return testLoc;
                }
            }
        }
        
        return playerLoc.clone().add(0, 2, 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Input input = inputs.remove(event.getPlayer().getUniqueId());
        if (input == null || !event.getBlock().equals(input.block())) return;

        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            value.append(event.getLine(i));
        }
        String raw = value.toString().trim();
        String colored = raw.isEmpty() ? "" : TextUtils.colorize(raw);
        
        if (TextUtils.plain(colored).length() > configManager.getConfigValues().profileMenu().maxLength()) {
            String message = configManager.getConfigValues().messages().tooLong()
                .replace("{max_length}", String.valueOf(configManager.getConfigValues().profileMenu().maxLength()));
            event.getPlayer().sendMessage(PlaceholderUtils.applypapi(event.getPlayer(), message, configManager));
            input.state().update(true, false);
            return;
        }

        input.state().update(true, false);
        Player player = event.getPlayer();
        String playerName = player.getName();
        
        scheduler.runAsync(() -> {
            PlayerProfile profile = repository.getPlayerProfile(playerName);
            
            if (colored.isEmpty()) {
                repository.savePlayerProfile(playerName, "", "");
                String message = input.discord() 
                    ? configManager.getConfigValues().messages().discordCleared()
                    : configManager.getConfigValues().messages().storeCleared();
                scheduler.runSync(() -> {
                    if (player.isOnline()) {
                        player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                    }
                });
                return;
            }
            
            repository.savePlayerProfile(playerName, 
                input.discord() ? colored : profile.discord(), 
                input.discord() ? profile.storeName() : colored);
            String messageTemplate = input.discord() 
                ? configManager.getConfigValues().messages().discordSet()
                : configManager.getConfigValues().messages().storeSet();
            String message = messageTemplate.replace("{tag}", colored);
            scheduler.runSync(() -> {
                if (player.isOnline()) {
                    player.sendMessage(PlaceholderUtils.applypapi(player, message, configManager));
                }
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        inputs.remove(event.getPlayer().getUniqueId());
    }

    private record Input(Block block, BlockState state, boolean discord) {
    }
}
