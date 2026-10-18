/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.inpatient.context;

import com.zxplat.plugin.booking.domain.bed.result.InpatientBedReservationSnapshot;
import com.zxplat.plugin.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentQueryService;
import com.zxplat.plugin.booking.domain.bed.entity.BedReservationExt;
import com.zxplat.plugin.booking.domain.bed.service.BedReservationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 住院床位预约读侧（orchestrator 专用）。
 */
@Component
@RequiredArgsConstructor
public class InpatientBedBookingReaderImpl {

    private final AppointmentQueryService appointmentQueryService;
    private final BedReservationQueryService bedReservationQueryService;

    public Optional<InpatientBedReservationSnapshot> findActiveByBedAndDate(
        Long bedLocationId,
        LocalDate intendedDate) {
        if (bedLocationId == null || intendedDate == null) {
            return Optional.empty();
        }
        return appointmentQueryService.findActiveBedBookingByBedAndDate(bedLocationId, intendedDate)
            .map(booking -> this.toSnapshot(booking))
            .filter(snapshot -> snapshot != null);
    }

    public Optional<InpatientBedReservationSnapshot> findActiveByBed(Long bedLocationId) {
        if (bedLocationId == null) {
            return Optional.empty();
        }
        List<BedReservationExt> extensions = bedReservationQueryService.findActiveByLocationId(bedLocationId);
        if (extensions.isEmpty()) {
            return Optional.empty();
        }
        extensions.sort(Comparator.comparing(
            BedReservationExt::getIntendedAdmissionDate,
            Comparator.nullsLast(Comparator.reverseOrder())));
        BedReservationExt ext = extensions.get(0);
        return Optional.ofNullable(this.toSnapshot(
            appointmentQueryService.loadBedBookingById(ext.getAppointmentId())));
    }

    private InpatientBedReservationSnapshot toSnapshot(AppointmentBookingDetailModel booking) {
        if (booking == null || booking.getId() == null) {
            return null;
        }
        return bedReservationQueryService.findByAppointmentId(booking.getId())
            .map(ext -> {
                InpatientBedReservationSnapshot snapshot = new InpatientBedReservationSnapshot();
                snapshot.setAppointmentId(booking.getId());
                snapshot.setBedLocationId(ext.getLocationId());
                snapshot.setPatientId(ext.getPatientId());
                snapshot.setIntendedAdmissionDate(ext.getIntendedAdmissionDate());
                return snapshot;
            })
            .orElse(null);
    }
}
