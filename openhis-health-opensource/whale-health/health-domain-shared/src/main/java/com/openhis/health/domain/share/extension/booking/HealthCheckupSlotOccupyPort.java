/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.booking;

import java.time.LocalDate;
import java.util.List;

/**
 * 体检号源占号 SPI（miniapp BFF → booking）。
 *
 * <p>实现落在 booking-application，委托 {@code HealthCheckupSlotOccupyAppService}。</p>
 */
public interface HealthCheckupSlotOccupyPort {

    /**
     * 查询可约日期。
     *
     * @param organizationId   机构
     * @param contractTypeCode 合同类型（含 WALK_IN）
     * @param campusCode       校区（可空）
     * @param fromDate         起
     * @param toDate           止
     * @return 可约日视图
     */
    List<HcBookableDayView> listBookableDays(
        Long organizationId,
        String contractTypeCode,
        String campusCode,
        LocalDate fromDate,
        LocalDate toDate);

    /**
     * 查询某日可约时段。
     *
     * @param organizationId   机构
     * @param planDate         日期
     * @param contractTypeCode 合同类型
     * @param campusCode       校区（可空）
     * @return 可约时段
     */
    List<HcBookableSlotView> listBookableSlots(
        Long organizationId, LocalDate planDate, String contractTypeCode, String campusCode);

    /**
     * 创建占号预约（须已校验会话患者）。
     *
     * @param command 下单命令
     * @return 预约结果
     */
    HcCreateBookingResult createBooking(HcCreateBookingCommand command);
}
