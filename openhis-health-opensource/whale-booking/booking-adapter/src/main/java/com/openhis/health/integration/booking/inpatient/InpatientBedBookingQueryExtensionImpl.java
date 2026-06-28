/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.inpatient;

import com.openhis.health.integration.booking.result.InpatientBedReservationSnapshot;
import com.openhis.whale.booking.contracts.reader.InpatientBedBookingReader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/** {@link InpatientBedBookingQueryExtension} 实现。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
public class InpatientBedBookingQueryExtensionImpl implements InpatientBedBookingQueryExtension {

    private final InpatientBedBookingReader inpatientBedBookingReader;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<InpatientBedReservationSnapshot> findActiveByBedAndDate(
        Long bedLocationId,
        LocalDate intendedDate) {
        return inpatientBedBookingReader.findActiveByBedAndDate(bedLocationId, intendedDate)
            .map(this::toSnapshot);
    }

    @Override
    public Optional<InpatientBedReservationSnapshot> findActiveByBed(Long bedLocationId) {
        return inpatientBedBookingReader.findActiveByBed(bedLocationId)
            .map(this::toSnapshot);
    }

    private InpatientBedReservationSnapshot toSnapshot(
        com.openhis.whale.booking.contracts.dto.InpatientBedReservationSnapshot source) {
        InpatientBedReservationSnapshot target = new InpatientBedReservationSnapshot();
        target.setAppointmentId(source.getAppointmentId());
        target.setBedLocationId(source.getBedLocationId());
        target.setPatientId(source.getPatientId());
        target.setIntendedAdmissionDate(source.getIntendedAdmissionDate());
        return target;
    }
}
