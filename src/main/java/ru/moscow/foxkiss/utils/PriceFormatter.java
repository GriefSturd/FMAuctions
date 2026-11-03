package ru.moscow.foxkiss.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public final class PriceFormatter {

    private static final DecimalFormat formatDecimals;
    private static final DecimalFormat formatWithoutDecimals;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator('.');

        formatDecimals = new DecimalFormat("#,##0.00", symbols);
        formatDecimals.setGroupingUsed(true);

        formatWithoutDecimals = new DecimalFormat("#,##0", symbols);
        formatWithoutDecimals.setGroupingUsed(true);
    }

    public static String format(double amount) {
        if (amount < 1_000) {
            return formatDecimals.format(amount);
        }
        return formatWithoutDecimals.format(amount);
    }

    public static String format(double amount, ru.moscow.foxkiss.auction.AuctionCurrency currency) {
        if (currency != null && currency.isIntegerOnly()) {
            return formatWithoutDecimals.format(Math.round(amount));
        }
        return format(amount);
    }
}
