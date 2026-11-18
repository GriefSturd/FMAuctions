
package ru.moscow.foxkiss.utils;

import ru.moscow.foxkiss.auction.AuctionCurrency;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class PriceFormatter {

    private static final ThreadLocal<DecimalFormat> DECIMALS = ThreadLocal.withInitial(() -> createFormat("#,##0.00"));

    private static final ThreadLocal<DecimalFormat> INTEGERS = ThreadLocal.withInitial(() -> createFormat("#,##0"));

    private static DecimalFormat createFormat(String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();

        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');

        return new DecimalFormat(pattern, symbols);
    }

    public static String format(double amount) {
        DecimalFormat formatter = amount < 1_000
                ? DECIMALS.get()
                : INTEGERS.get();

        return formatter.format(amount);
    }

    public static String format(double amount, AuctionCurrency currency) {
        if (currency != null && currency.isIntegerOnly()) {
            return INTEGERS.get().format(Math.round(amount));
        }

        return format(amount);
    }
}
