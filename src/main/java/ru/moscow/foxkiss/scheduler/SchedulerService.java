package ru.moscow.foxkiss.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

public final class SchedulerService {

    private final JavaPlugin plugin;

    private final ExecutorService dbExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SchedulerService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void runAsync(Runnable task) {
        if (!plugin.isEnabled()) return;

        try {
            dbExecutor.execute(() -> execute(task, "Ошибка асинхронной задачи"));
        } catch (RejectedExecutionException ignored) {
        }
    }

    public void runSync(Runnable task) {
        if (!plugin.isEnabled()) return;

        if (Bukkit.isPrimaryThread()) {
            execute(task, "Ошибка синхронной задачи");
        } else {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> execute(task, "Ошибка синхронной задачи"));
            } catch (IllegalPluginAccessException ignored) {
            }
        }
    }

    public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) return;

        try {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> execute(task, "Ошибка периодической задачи"), delayTicks, periodTicks);
        } catch (IllegalPluginAccessException ignored) {
        }
    }

    private void execute(Runnable task, String errorMessage) {
        try {
            task.run();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, errorMessage, e);
        }
    }

    public void shutdown() {
        dbExecutor.shutdown();
    }
}