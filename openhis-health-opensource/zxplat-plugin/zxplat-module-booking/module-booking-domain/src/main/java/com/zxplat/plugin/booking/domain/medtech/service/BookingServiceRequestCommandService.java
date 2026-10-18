/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.medtech.service;

/**
 * 医技医嘱预约回写；实现由 booking-bridge 提供。
 */
public interface BookingServiceRequestCommandService {

    void markServiceRequestBooked(Long serviceRequestId, Long appointmentId);
}
