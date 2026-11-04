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
        tryRunSync(task);
    }

    private boolean tryRunSync(Runnable task) {
        if (!plugin.isEnabled()) return false;
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
            return true;
        } catch (IllegalPluginAccessException ignored) {
            return false;
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
        runAsyncThenSync(asyncTask, syncCallback, error -> plugin.getLogger().log(Level.SEVERE, "Ошибка в асинхронной части", error));
    }

    public <T> void runAsyncThenSync(Supplier<T> asyncTask, Consumer<T> syncCallback, Consumer<Throwable> onError) {
        if (dbExecutor.isShutdown()) {
            onError.accept(new java.util.concurrent.RejectedExecutionException("Планировщик завершает работу"));
            return;
        }
        try {
            dbExecutor.execute(() -> {
                try {
                    T result = asyncTask.get();
                    if (!plugin.isEnabled()) {
                        onError.accept(new java.util.concurrent.RejectedExecutionException("Плагин отключён"));
                        return;
                    }
                    if (!tryRunSync(() -> {
                        try { syncCallback.accept(result); }
                        catch (Throwable e) { onError.accept(e); }
                    })) onError.accept(new java.util.concurrent.RejectedExecutionException("Не удалось запланировать обновление меню"));
                } catch (Throwable e) { onError.accept(e); }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) { onError.accept(e); }
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