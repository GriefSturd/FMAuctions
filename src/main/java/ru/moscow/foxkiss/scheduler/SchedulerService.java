package ru.moscow.foxkiss.scheduler;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        dbExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка в асинхронной задаче", e);
            }
        });
    }

    public void runSync(Runnable task) {
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
        runnable.runTask(plugin);
    }

    public <T> void runAsyncThenSync(Supplier<T> asyncTask, Consumer<T> syncCallback) {
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
    }
}