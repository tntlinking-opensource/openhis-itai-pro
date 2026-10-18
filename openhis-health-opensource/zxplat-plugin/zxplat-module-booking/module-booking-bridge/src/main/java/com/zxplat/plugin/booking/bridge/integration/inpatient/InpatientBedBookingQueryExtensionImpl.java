/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.integration.inpatient;

import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.InpatientBedBookingByBedExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.InpatientBedBookingQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionOut.InpatientBedReservationQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.booking.inpatient.InpatientBedBookingQueryExtension;
import com.zxplat.plugin.booking.bridge.integration.mapper.BookingIntegrationMapper;
import com.zxplat.plugin.booking.bridge.inpatient.workflow.BookingInpatientBedQueryOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@link InpatientBedBookingQueryExtension} 实现，委托 {@link BookingInpatientBedQueryOrchestrator}。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(BookingInpatientBedQueryOrchestrator.class)
@ConditionalOnProperty(name = "zxplat.modules.booking.enabled", havingValue = "true", matchIfMissing = true)
public class InpatientBedBookingQueryExtensionImpl implements InpatientBedBookingQueryExtension {

    private final BookingInpatientBedQueryOrchestrator bedQueryOrchestrator;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<InpatientBedReservationQueryExtensionOut> findActiveByBedAndDate(
            InpatientBedBookingQueryExtensionIn query) {
        return bedQueryOrchestrator.findActiveByBedAndDate(query.getBedLocationId(), query.getIntendedDate())
                .map(BookingIntegrationMapper::toBedReservationSnapshot);
    }

    @Override
    public Optional<InpatientBedReservationQueryExtensionOut> findActiveByBed(
            InpatientBedBookingByBedExtensionIn query) {
        return bedQueryOrchestrator.findActiveByBed(query.getBedLocationId())
                .map(BookingIntegrationMapper::toBedReservationSnapshot);
    }
}
