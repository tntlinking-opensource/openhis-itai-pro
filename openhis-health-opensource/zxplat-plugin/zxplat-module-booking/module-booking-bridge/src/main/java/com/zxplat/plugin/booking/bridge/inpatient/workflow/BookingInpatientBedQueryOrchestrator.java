/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.inpatient.workflow;

import com.zxplat.plugin.booking.bridge.inpatient.context.InpatientBedBookingReaderImpl;
import com.zxplat.plugin.booking.domain.bed.result.InpatientBedReservationSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 住院床位预约查询编排（护士站分床前校验）。
 *
 * <p>职责：按床位与日期检索有效预留快照；读侧委托 {@link InpatientBedBookingReaderImpl}。</p>
 */
@Component
@RequiredArgsConstructor
public class BookingInpatientBedQueryOrchestrator {

    private final InpatientBedBookingReaderImpl inpatientBedBookingReader;

    /** 按床位与意向入院日检索有效预留。 */
    public Optional<InpatientBedReservationSnapshot> findActiveByBedAndDate(Long bedLocationId, LocalDate intendedDate) {
        return inpatientBedBookingReader.findActiveByBedAndDate(bedLocationId, intendedDate);
    }

    /** 按床位检索当前有效预留（不限日期）。 */
    public Optional<InpatientBedReservationSnapshot> findActiveByBed(Long bedLocationId) {
        return inpatientBedBookingReader.findActiveByBed(bedLocationId);
    }
}
