/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.booking.outpatient;

import com.openhis.health.infrastructure.integration.booking.outpatient.OutpatientBookingRegistrationExtension;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.OutpatientRegistrationCancelExtensionIn;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.OutpatientWalkInSlotAllocateExtensionIn;
import com.openhis.health.integration.booking.mapper.BookingIntegrationMapper;
import com.openhis.whale.booking.bridge.outpatient.workflow.BookingOutpatientRegistrationOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** {@link OutpatientBookingRegistrationExtension} 实现，委托 {@link BookingOutpatientRegistrationOrchestrator}。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(BookingOutpatientRegistrationOrchestrator.class)
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
public class OutpatientBookingRegistrationExtensionImpl implements OutpatientBookingRegistrationExtension {

    private final BookingOutpatientRegistrationOrchestrator registrationOrchestrator;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void linkEncounter(Long appointmentId, Long encounterId) {
        registrationOrchestrator.linkEncounter(appointmentId, encounterId);
    }

    @Override
    public Long allocateWalkInSlot(OutpatientWalkInSlotAllocateExtensionIn command) {
        return registrationOrchestrator.allocateWalkInSlot(BookingIntegrationMapper.toWalkInCommand(command));
    }

    @Override
    public void checkInAfterRegister(Long appointmentId, Long encounterId) {
        registrationOrchestrator.checkInAfterRegister(appointmentId, encounterId);
    }

    @Override
    public void onRegistrationCancelled(Long encounterId, OutpatientRegistrationCancelExtensionIn reason) {
        registrationOrchestrator.onRegistrationCancelled(
                encounterId, BookingIntegrationMapper.toCancelReason(reason));
    }
}
