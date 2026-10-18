/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.share;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 预约自然日口径（GAP-BKG-001：固定 Asia/Shanghai，与挂号防重对齐）。
 */
public final class BookingDateSupport {

    public static final ZoneId BOOKING_ZONE = ZoneId.of("Asia/Shanghai");

    private BookingDateSupport() {
    }

    /**
     * 由计划开始时刻解析预约自然日。
     *
     * @param startInstant 计划开始时刻
     * @return 预约自然日；startInstant 为空时取当前上海自然日
     */
    public static LocalDate resolveBookingDate(LocalDateTime startInstant) {
        if (startInstant == null) {
            return LocalDate.now(BOOKING_ZONE);
        }
        return startInstant.atZone(BOOKING_ZONE).toLocalDate();
    }
}
