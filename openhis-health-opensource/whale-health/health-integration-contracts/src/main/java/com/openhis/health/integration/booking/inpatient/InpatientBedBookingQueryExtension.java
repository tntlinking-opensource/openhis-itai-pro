/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.inpatient;

import com.openhis.health.integration.booking.result.InpatientBedReservationSnapshot;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 住院护士站分床读床位预约（GAP-ATD-007）。
 */
public interface InpatientBedBookingQueryExtension {

    default boolean isEnabled() {
        return false;
    }

    default Optional<InpatientBedReservationSnapshot> findActiveByBedAndDate(
        Long bedLocationId,
        LocalDate intendedDate) {
        return Optional.empty();
    }

    default Optional<InpatientBedReservationSnapshot> findActiveByBed(Long bedLocationId) {
        return Optional.empty();
    }
}
