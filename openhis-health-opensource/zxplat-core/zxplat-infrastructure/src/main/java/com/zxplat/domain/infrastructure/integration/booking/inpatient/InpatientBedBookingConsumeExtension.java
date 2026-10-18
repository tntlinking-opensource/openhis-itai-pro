/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.inpatient;

/**
 * 住院分床消费床位预约写 Extension（GAP-ATD-007）。
 */
public interface InpatientBedBookingConsumeExtension {

    /**
     * 住院床位预约消费扩展是否已装配。
     *
     * @return false 时分床不回调 booking 占号/核销
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 分床成功后消费（核销）床位预约。
     *
     * @param appointmentId 床位预约主键
     * @param operatorId    分床操作员主键
     */
    default void consumeReservationForBedAssign(Long appointmentId, Long operatorId) {
        // booking 未装配时无操作
    }
}
