/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.outpatient;

import com.openhis.health.integration.booking.command.OutpatientRegistrationCancelCommand;
import com.openhis.health.integration.booking.command.OutpatientWalkInSlotAllocateCommand;
import com.openhis.health.integration.booking.mapper.BookingIntegrationMapper;
import com.openhis.whale.booking.contracts.writer.OutpatientBookingWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** {@link OutpatientBookingRegistrationExtension} 实现，委托 {@link OutpatientBookingWriter}。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
public class OutpatientBookingRegistrationExtensionImpl implements OutpatientBookingRegistrationExtension {

    private final OutpatientBookingWriter outpatientBookingWriter;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void linkEncounter(Long appointmentId, Long encounterId) {
        outpatientBookingWriter.linkEncounter(appointmentId, encounterId);
    }

    @Override
    public Long allocateWalkInSlot(OutpatientWalkInSlotAllocateCommand command) {
        return outpatientBookingWriter.allocateWalkInSlot(BookingIntegrationMapper.toWalkInCommand(command));
    }

    @Override
    public void checkInAfterRegister(Long appointmentId, Long encounterId) {
        outpatientBookingWriter.checkInAfterRegister(appointmentId, encounterId);
    }

    @Override
    public void onRegistrationCancelled(Long encounterId, OutpatientRegistrationCancelCommand reason) {
        outpatientBookingWriter.onRegistrationCancelled(
                encounterId, BookingIntegrationMapper.toCancelReason(reason));
    }
}
