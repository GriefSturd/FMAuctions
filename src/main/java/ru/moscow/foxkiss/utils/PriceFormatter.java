package ru.moscow.foxkiss.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class PriceFormatter {

    private static final DecimalFormat formatDecimals;
    private static final DecimalFormat formatWithoutDecimals;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator('.');

        formatDecimals = new DecimalFormat("#,##0.00", symbols);
        formatDecimals.setGroupingUsed(true);

        formatWithoutDecimals = new DecimalFormat("#,##0", symbols);
        formatWithoutDecimals.setGroupingUsed(true);
    }

    private PriceFormatter() {}

    public static String format(double amount) {
        if (amount < 1_000) {
            return formatDecimals.format(amount);
        }
        return formatWithoutDecimals.format(amount);
    }
}
