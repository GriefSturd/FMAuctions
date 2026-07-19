package ru.moscow.foxkiss.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class PriceFormatter {

    private static final DecimalFormat FORMAT_WITH_DECIMALS;
    private static final DecimalFormat FORMAT_WITHOUT_DECIMALS;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator('.');

        FORMAT_WITH_DECIMALS = new DecimalFormat("#,##0.00", symbols);
        FORMAT_WITH_DECIMALS.setGroupingUsed(true);

        FORMAT_WITHOUT_DECIMALS = new DecimalFormat("#,##0", symbols);
        FORMAT_WITHOUT_DECIMALS.setGroupingUsed(true);
    }

    private PriceFormatter() {}

    public static String format(double amount) {
        if (amount < 999) {
            return FORMAT_WITH_DECIMALS.format(amount);
        }
        return FORMAT_WITHOUT_DECIMALS.format(amount);
    }
}