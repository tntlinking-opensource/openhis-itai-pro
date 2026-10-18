/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.service;

import com.zxplat.plugin.booking.domain.medtech.service.BookingServiceRequestCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingServiceRequestCommandServiceImpl implements BookingServiceRequestCommandService {

    @Override
    public void markServiceRequestBooked(Long serviceRequestId, Long appointmentId) {
        // health-domain 暂无医嘱预约回写命令；关联由 booking-domain Appointment 承载。
    }
}
