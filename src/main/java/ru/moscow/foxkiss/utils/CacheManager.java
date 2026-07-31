package ru.moscow.foxkiss.utils;

import java.util.*;
public final class CacheManager {

    private final List<Runnable> clearTasks = new ArrayList<>();
    private final Map<UUID, List<Runnable>> playerClearTasks = new HashMap<>();

    public void registerClearTask(Runnable task) {
        clearTasks.add(task);
    }

    public void clearAll() {
        clearTasks.forEach(Runnable::run);
    }

    public void clearForPlayer(UUID playerId) {
        List<Runnable> tasks = playerClearTasks.remove(playerId);
        if (tasks != null) {
            tasks.forEach(Runnable::run);
        }
    }

    public void removePlayerTasks(UUID playerId) {
        playerClearTasks.remove(playerId);
    }
}