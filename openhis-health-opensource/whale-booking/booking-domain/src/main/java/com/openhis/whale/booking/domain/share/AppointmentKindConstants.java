package com.openhis.whale.booking.domain.share;

/**
 * 预约分类常量（appointment_kind 存 code）。
 */
public final class AppointmentKindConstants {

    public static final String OUTPATIENT_BOOKING = "OUTPATIENT_BOOKING";
    public static final String CHECK_IN_ONLY = "CHECK_IN_ONLY";
    public static final String DIAGNOSTIC_BOOKING = "DIAGNOSTIC_BOOKING";
    public static final String BED_BOOKING = "BED_BOOKING";
    public static final String HEALTH_CHECKUP_BOOKING = "HEALTH_CHECKUP_BOOKING";

    private AppointmentKindConstants() {
    }
}
