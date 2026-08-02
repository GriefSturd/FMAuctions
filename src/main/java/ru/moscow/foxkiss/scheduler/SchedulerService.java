package ru.moscow.foxkiss.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Упрощённый сервис для управления async/sync операциями.
 * 
 * Правила:
 * - Async: SQL, файловая система, сеть, долгие вычисления
 * - Sync: Bukkit API, Player, Inventory, World
 */
public final class SchedulerService {

    private final JavaPlugin plugin;

    public SchedulerService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Выполнить задачу асинхронно.
     */
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    /**
     * Выполнить задачу синхронно.
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Паттерн: Async (SQL) → Sync (Bukkit API).
     * 
     * @param asyncTask задача для выполнения асинхронно
     * @param syncCallback коллбэк для обработки результата синхронно
     */
    public <T> void runAsyncThenSync(Supplier<T> asyncTask, Consumer<T> syncCallback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T result = asyncTask.get();
            Bukkit.getScheduler().runTask(plugin, () -> syncCallback.accept(result));
        });
    }
}
