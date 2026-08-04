package ru.moscow.foxkiss.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SchedulerService {

    private final JavaPlugin plugin;

    public SchedulerService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public <T> void runAsyncThenSync(Supplier<T> asyncTask, Consumer<T> syncCallback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T result = asyncTask.get();
            Bukkit.getScheduler().runTask(plugin, () -> syncCallback.accept(result));
        });
    }
}
