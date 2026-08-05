package ru.moscow.foxkiss.gui.enums;

public enum ActionType {
    MAIN,
    SELLING,
    EXPIRED,
    SORT,
    CATEGORIES,
    PREVIOUS,
    NEXT,
    REFRESH,
    CONFIRM,
    CANCEL,
    DECREASE_10,
    DECREASE_1,
    INCREASE_1,
    INCREASE_10,
    BUY;

    public static ActionType get(String string) {
        try {
            return valueOf(string.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}