
package ru.moscow.foxkiss.gui;

import ru.moscow.foxkiss.config.ConfigValues.LotAgeConfig;

public final class LotDisplayTime {

    public static long ageBucket(long createdAt, long now) {
        long elapsed = Math.max(0L, now - createdAt);

        if (elapsed < 3_600_000) {
            return elapsed / 60_000;
        }

        if (elapsed < 86_400_000) {
            return 60 + elapsed / 3_600_000;
        }

        return 84 + elapsed / 86_400_000;
    }

    public static long daysLeft(long createdAt, long now, int maxStorageDays) {
        long elapsedDays = Math.max(0, now - createdAt) / 86_400_000;

        return Math.max(0, (long) maxStorageDays - elapsedDays);
    }

    public static String formatAge(long bucket, LotAgeConfig templates) {
        if (bucket < 60) {
            return templates.minutes().replace("{amount}", Long.toString(bucket));
        }

        if (bucket < 84) {
            return templates.hours().replace("{amount}", Long.toString(bucket - 60));
        }

        return templates.days().replace("{amount}", Long.toString(bucket - 84));
    }
}
