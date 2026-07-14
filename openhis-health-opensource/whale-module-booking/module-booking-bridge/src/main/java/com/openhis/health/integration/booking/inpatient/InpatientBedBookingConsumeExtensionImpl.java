/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.booking.inpatient;

import com.openhis.health.infrastructure.integration.booking.inpatient.InpatientBedBookingConsumeExtension;
import com.openhis.whale.booking.bridge.inpatient.workflow.BookingInpatientBedConsumeOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** {@link InpatientBedBookingConsumeExtension} 实现，委托 {@link BookingInpatientBedConsumeOrchestrator}。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(BookingInpatientBedConsumeOrchestrator.class)
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
public class InpatientBedBookingConsumeExtensionImpl implements InpatientBedBookingConsumeExtension {

    private final BookingInpatientBedConsumeOrchestrator bedConsumeOrchestrator;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void consumeReservationForBedAssign(Long appointmentId, Long operatorId) {
        bedConsumeOrchestrator.consumeReservationForBedAssign(appointmentId, operatorId);
    }
}
