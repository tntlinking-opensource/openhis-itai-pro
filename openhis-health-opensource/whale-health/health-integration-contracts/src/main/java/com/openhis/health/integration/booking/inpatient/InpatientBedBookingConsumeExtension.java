/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.inpatient;

/**
 * 住院分床消费床位预约写 Extension（GAP-ATD-007）。
 */
public interface InpatientBedBookingConsumeExtension {

    default boolean isEnabled() {
        return false;
    }

    default void consumeReservationForBedAssign(Long appointmentId, Long operatorId) {
        // booking 未装配时无操作
    }
}
