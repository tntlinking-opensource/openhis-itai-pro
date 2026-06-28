/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.inpatient;

import com.openhis.whale.booking.contracts.writer.InpatientBedBookingConsumeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** {@link InpatientBedBookingConsumeExtension} 实现。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
public class InpatientBedBookingConsumeExtensionImpl implements InpatientBedBookingConsumeExtension {

    private final InpatientBedBookingConsumeWriter inpatientBedBookingConsumeWriter;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void consumeReservationForBedAssign(Long appointmentId, Long operatorId) {
        inpatientBedBookingConsumeWriter.consumeReservationForBedAssign(appointmentId, operatorId);
    }
}
