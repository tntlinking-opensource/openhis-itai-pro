/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.booking.inpatient;

import com.openhis.health.infrastructure.integration.booking.ExtensionIn.InpatientBedBookingByBedExtensionIn;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.InpatientBedBookingQueryExtensionIn;
import com.openhis.health.infrastructure.integration.booking.ExtensionOut.InpatientBedReservationQueryExtensionOut;

import java.util.Optional;

/** 住院护士站分床读床位预约（GAP-ATD-007）。 */
public interface InpatientBedBookingQueryExtension {

    default boolean isEnabled() {
        return false;
    }

    default Optional<InpatientBedReservationQueryExtensionOut> findActiveByBedAndDate(
            InpatientBedBookingQueryExtensionIn query) {
        return Optional.empty();
    }

    default Optional<InpatientBedReservationQueryExtensionOut> findActiveByBed(
            InpatientBedBookingByBedExtensionIn query) {
        return Optional.empty();
    }
}
