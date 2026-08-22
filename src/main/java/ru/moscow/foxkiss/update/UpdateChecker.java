package ru.moscow.foxkiss.update;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker implements Listener {
    private static final String urlrealeases = "https://api.github.com/repos/GriefSturd/FMAuctions/releases/latest";
    private static final String realeasepage = "https://github.com/GriefSturd/FMAuctions/releases/latest";
    private static final Pattern tag_pattern = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final JavaPlugin plugin;
    private volatile String latestVersion;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("update-checker", true)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(urlrealeases)).header("Accept", "application/vnd.github+json").header("User-Agent", plugin.getName() + " update checker").timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("Не можем отправить запрос на проверку версии: " + response.statusCode() + ".");
                    return;
                }

                Matcher matcher = tag_pattern.matcher(response.body());
                if (!matcher.find()) {
                    plugin.getLogger().warning("Unable to check for updates: GitHub response has no release tag.");
                    return;
                }

                String version = matcher.group(1);
                String installedVersion = plugin.getPluginMeta().getVersion();
                if (isNewer(version, installedVersion)) {
                    latestVersion = version;
                    plugin.getLogger().warning("[" + plugin.getName() + "] Доступно обновление " + latestVersion + ". (Установлено)" + installedVersion + " Скачать: " + realeasepage);
                }
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                plugin.getLogger().warning("Unable to check for updates: " + exception.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (latestVersion != null && player.hasPermission("fmauction.update")) {
            player.sendMessage("§6[" + plugin.getName() + "] §eДоступно обновление §f" + latestVersion + "§e. Скачать: §b" + realeasepage);
        }
    }

    private boolean isNewer(String candidate, String installed) {
        String[] candidateParts = normalize(candidate).split("\\.");
        String[] installedParts = normalize(installed).split("\\.");
        int length = Math.max(candidateParts.length, installedParts.length);

        for (int i = 0; i < length; i++) {
            int candidatePart = i < candidateParts.length ? parsePart(candidateParts[i]) : 0;
            int installedPart = i < installedParts.length ? parsePart(installedParts[i]) : 0;
            if (candidatePart != installedPart) return candidatePart > installedPart;
        }
        return false;
    }

    private String normalize(String version) {
        return version.replaceFirst("^[vV]", "").replaceFirst("[-+].*$", "");
    }

    private int parsePart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
