package ru.moscow.foxkiss.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F\\d]{6})");
    private static final char COLOR_CHAR = '\u00A7';

    public static boolean isNotBlank(String str) {
        return str != null && !str.isEmpty();
    }

    public static boolean isBlank(String str) {
        return str == null || str.isEmpty();
    }

    public static String colorize(String message) {
        if (message == null || message.isEmpty()) return message;

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 32);

        while (matcher.find()) {
            String hex = matcher.group(1);

            matcher.appendReplacement(
                    buffer,
                    COLOR_CHAR + "x"
                            + COLOR_CHAR + hex.charAt(0)
                            + COLOR_CHAR + hex.charAt(1)
                            + COLOR_CHAR + hex.charAt(2)
                            + COLOR_CHAR + hex.charAt(3)
                            + COLOR_CHAR + hex.charAt(4)
                            + COLOR_CHAR + hex.charAt(5)
            );
        }

        matcher.appendTail(buffer);
        return translateAlternateColorCodes('&', buffer.toString());
    }

    public static String translateAlternateColorCodes(char altColorChar, String text) {
        char[] chars = text.toCharArray();

        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == altColorChar && isValidColorCharacter(chars[i + 1])) {
                chars[i] = COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }

        return new String(chars);
    }

    private static boolean isValidColorCharacter(char c) {
        return switch (c) {
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                 'a', 'b', 'c', 'd', 'e', 'f',
                 'r', 'k', 'l', 'm', 'n', 'o',
                 'x' -> true;
            case 'A', 'B', 'C', 'D', 'E', 'F',
                 'R', 'K', 'L', 'M', 'N', 'O', 'X' -> true;
            default -> false;
        };
    }

    public static Component component(String message) {
        if (message == null) return Component.empty();
        String colored = colorize(message);
        return LegacyComponentSerializer.legacySection()
                .deserialize(colored)
                .decoration(TextDecoration.ITALIC, false);
    }
}
