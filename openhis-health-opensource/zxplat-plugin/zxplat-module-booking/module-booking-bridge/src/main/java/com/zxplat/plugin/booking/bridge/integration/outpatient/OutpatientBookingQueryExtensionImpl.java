/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.integration.outpatient;

import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.BookableServiceTypesQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.BookableSlotsQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientBookingLoadExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientBookingLookupExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientBookingRegistrationValidateExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.BookableServiceOptionQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.BookableSlotHintQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.OutpatientBookingQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.booking.outpatient.OutpatientBookingQueryExtension;
import com.zxplat.plugin.booking.bridge.integration.mapper.BookingIntegrationMapper;
import com.zxplat.plugin.booking.bridge.outpatient.workflow.BookingOutpatientQueryOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** {@link OutpatientBookingQueryExtension} 实现，委托 {@link BookingOutpatientQueryOrchestrator}。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(BookingOutpatientQueryOrchestrator.class)
@ConditionalOnProperty(name = "zxplat.modules.booking.enabled", havingValue = "true", matchIfMissing = true)
public class OutpatientBookingQueryExtensionImpl implements OutpatientBookingQueryExtension {

    private final BookingOutpatientQueryOrchestrator outpatientQueryOrchestrator;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<OutpatientBookingQueryExtensionOut> findActiveOutpatientBooking(
            OutpatientBookingLookupExtensionIn command) {
        return outpatientQueryOrchestrator.findActiveOutpatientBooking(BookingIntegrationMapper.toLookupQuery(command))
                .map(BookingIntegrationMapper::toSnapshot);
    }

    @Override
    public Optional<OutpatientBookingQueryExtensionOut> loadOutpatientBookingById(
            OutpatientBookingLoadExtensionIn query) {
        return outpatientQueryOrchestrator.loadOutpatientBookingById(query.getAppointmentId())
                .map(BookingIntegrationMapper::toSnapshot);
    }

    @Override
    public List<BookableServiceOptionQueryExtensionOut> listBookableServiceTypes(
            BookableServiceTypesQueryExtensionIn query) {
        return BookingIntegrationMapper.toServiceOptionSnapshots(
                outpatientQueryOrchestrator.listBookableServiceTypes(
                        query.getOrganizationId(), query.getPractitionerId(), query.getDate()));
    }

    @Override
    public List<BookableSlotHintQueryExtensionOut> listBookableSlots(BookableSlotsQueryExtensionIn query) {
        return BookingIntegrationMapper.toSlotHintSnapshots(
                outpatientQueryOrchestrator.listBookableSlots(
                        query.getOrganizationId(),
                        query.getPractitionerId(),
                        query.getHealthcareServiceId(),
                        query.getDate()));
    }

    @Override
    public void validateForRegistration(OutpatientBookingRegistrationValidateExtensionIn command) {
        outpatientQueryOrchestrator.validateForRegistration(BookingIntegrationMapper.toValidateCommand(command));
    }

    @Override
    public boolean shouldIntersectRegisterServicesWithReleasedSlots(Long organizationId) {
        // 挂号 IF-REG-01 与排班解绑：不再按院点模式与放号求交
        return outpatientQueryOrchestrator.shouldIntersectRegisterServicesWithReleasedSlots(organizationId);
    }

    @Override
    public boolean shouldAllocateWalkInSlotForWalkInRegistration(Long organizationId) {
        return outpatientQueryOrchestrator.shouldAllocateWalkInSlotForWalkInRegistration(organizationId);
    }
}
