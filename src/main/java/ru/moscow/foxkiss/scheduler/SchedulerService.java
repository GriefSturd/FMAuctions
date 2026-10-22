package ru.moscow.foxkiss.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class SchedulerService {
    private final JavaPlugin plugin;

    private final ExecutorService dbExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SchedulerService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void runAsync(Runnable task) {
        if (dbExecutor.isShutdown()) return;
        dbExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка в асинхронной задаче", e);
            }
        });
    }

    public void runSync(Runnable task) {
        if (!plugin.isEnabled()) return;
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка в синхронной задаче", e);
                }
            }
        };
        try {
            runnable.runTask(plugin);
        } catch (IllegalPluginAccessException ignored) {
        }
    }

    public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка в периодической асинхронной задаче", e);
            }
        }, delayTicks, periodTicks);
    }

    public <T> void runAsyncThenSync(Supplier<T> asyncTask, Consumer<T> syncCallback) {
        if (dbExecutor.isShutdown()) return;
        dbExecutor.execute(() -> {
            T result = null;
            Throwable error = null;
            try {
                result = asyncTask.get();
            } catch (Throwable t) {
                error = t;
            }
            final T finalResult = result;
            final Throwable finalError = error;
            runSync(() -> {
                if (finalError != null) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка в асинхронной части", finalError);
                } else {
                    syncCallback.accept(finalResult);
                }
            });
        });
    }

    public void shutdown() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}