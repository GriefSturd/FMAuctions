package ru.moscow.foxkiss.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Сервис для управления синхронными и асинхронными операциями в Bukkit.
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
     * Выполнить задачу асинхронно (в отдельном потоке).
     * Используйте для SQL, файлов, сети.
     */
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    /**
     * Выполнить задачу синхронно (в главном потоке).
     * Используйте для Bukkit API.
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Выполнить асинхронную задачу, затем синхронный коллбэк с результатом.
     * 
     * Паттерн: Async (SQL) → Sync (Bukkit API)
     * 
     * @param asyncTask задача для выполнения асинхронно (возвращает результат)
     * @param syncCallback коллбэк для обработки результата синхронно
     */
    public <T> void runAsyncThenSync(Supplier<T> asyncTask, Consumer<T> syncCallback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T result = asyncTask.get();
            Bukkit.getScheduler().runTask(plugin, () -> syncCallback.accept(result));
        });
    }

    /**
     * Выполнить синхронную задачу, затем асинхронный коллбэк с результатом.
     * 
     * Паттерн: Sync (Bukkit API) → Async (SQL)
     * 
     * @param syncTask задача для выполнения синхронно (возвращает результат)
     * @param asyncCallback коллбэк для обработки результата асинхронно
     */
    public <T> void runSyncThenAsync(Supplier<T> syncTask, Consumer<T> asyncCallback) {
        runSync(() -> {
            T result = syncTask.get();
            runAsync(() -> asyncCallback.accept(result));
        });
    }

    /**
     * Цепочка: Async → Sync → Async
     * 
     * Типичный кейс: SQL → Bukkit API проверки → SQL запись
     */
    public <T, R> void runAsyncSyncAsync(
            Supplier<T> asyncTask,
            java.util.function.Function<T, R> syncTask,
            Consumer<R> asyncCallback) {
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T asyncResult = asyncTask.get();
            Bukkit.getScheduler().runTask(plugin, () -> {
                R syncResult = syncTask.apply(asyncResult);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> 
                    asyncCallback.accept(syncResult)
                );
            });
        });
    }

    /**
     * Запустить асинхронную задачу и вернуть CompletableFuture.
     * Для более сложных async цепочек.
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Запустить синхронную задачу и вернуть CompletableFuture.
     */
    public <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runSync(() -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Удобный метод для безопасного выполнения sync задачи после async.
     * Автоматически проверяет что игрок онлайн и т.д.
     */
    public <T> void runAsyncThenSyncSafe(
            Supplier<T> asyncTask, 
            java.util.function.Predicate<T> shouldProceed,
            Consumer<T> syncCallback) {
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T result = asyncTask.get();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (shouldProceed.test(result)) {
                    syncCallback.accept(result);
                }
            });
        });
    }

    /**
     * Запланировать задачу с задержкой (в тиках, 20 тиков = 1 секунда).
     */
    public void runLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    /**
     * Запланировать повторяющуюся задачу.
     * 
     * @param task задача
     * @param delayTicks задержка до первого выполнения
     * @param periodTicks период между выполнениями
     * @return ID задачи (для отмены через cancelTask)
     */
    public int runTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
    }

    /**
     * Отменить задачу по ID.
     */
    public void cancelTask(int taskId) {
        Bukkit.getScheduler().cancelTask(taskId);
    }
}
