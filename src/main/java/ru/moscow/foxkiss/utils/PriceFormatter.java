package ru.moscow.foxkiss.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class PriceFormatter {

    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        FORMAT = new DecimalFormat("#,##0", symbols);
        FORMAT.setGroupingUsed(true);
    }

    private PriceFormatter() {}

    public static String format(double amount) {
        long rounded = Math.round(amount);
        return FORMAT.format(rounded);
    }
}