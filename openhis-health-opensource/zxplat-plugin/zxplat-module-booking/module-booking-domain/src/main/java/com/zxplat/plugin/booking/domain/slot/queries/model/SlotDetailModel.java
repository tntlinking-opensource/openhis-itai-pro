/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 号源时段明细模型（DM-WKF-010）。
 */
@Getter
@Setter
public class SlotDetailModel {

    /** 时段标识。 */
    private Long id;

    /** 机构标识。 */
    private Long organizationId;

    /** 日程标识。 */
    private Long scheduleId;

    /** 开始时刻。 */
    private LocalDateTime startTime;

    /** 结束时刻。 */
    private LocalDateTime endTime;

    /** 状态码。 */
    private String statusCode;

    /** 容量。 */
    private Integer capacity;

    /** 已占用数。 */
    private Integer bookedCount;

    /** 超售标记。 */
    private Boolean overbookedFlag;

    /** 服务大类快照 JSON。 */
    private String serviceCategoryJson;

    /** 服务类型快照 JSON。 */
    private String serviceTypeJson;

    /** 专科快照 JSON。 */
    private String specialtyJson;

    /** 预约类型快照 JSON。 */
    private String appointmentTypeJson;

    /** 备注。 */
    private String commentText;
}
