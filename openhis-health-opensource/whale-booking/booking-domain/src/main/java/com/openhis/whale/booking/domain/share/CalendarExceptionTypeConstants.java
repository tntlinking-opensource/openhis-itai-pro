package com.openhis.whale.booking.domain.share;

/**
 * T3 日历例外类型（07 §5.4）。
 */
public final class CalendarExceptionTypeConstants {

    public static final String CLOSED = "CLOSED";
    public static final String REDUCED = "REDUCED";
    public static final String OVERRIDE = "OVERRIDE";
    public static final String EXTRA = "EXTRA";

    private CalendarExceptionTypeConstants() {
    }

    public static boolean isValid(String type) {
        return CLOSED.equals(type) || REDUCED.equals(type) || OVERRIDE.equals(type) || EXTRA.equals(type);
    }

    public static boolean requiresOverrideTemplate(String type) {
        return REDUCED.equals(type) || OVERRIDE.equals(type) || EXTRA.equals(type);
    }
}
