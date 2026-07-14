/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.slot.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 号源列表行 DTO（日历表格）。
 */
@Getter
@Setter
public class SlotListItemDto {

    /** 号源主键。 */
    private Long slotId;

    /** 日程主键。 */
    private Long scheduleId;

    /** 机构主键。 */
    private Long organizationId;

    /** 开始时刻。 */
    private LocalDateTime startTime;

    /** 结束时刻。 */
    private LocalDateTime endTime;

    /** 状态码（free / busy / BUSY_UNAVAILABLE 等）。 */
    private String statusCode;

    /** 容量。 */
    private Integer capacity;

    /** 已约数。 */
    private Integer bookedCount;

    /** 剩余可约数。 */
    private Integer remainingCount;

    /** 展示状态（可约 / 已满 / 停诊）。 */
    private String displayStatus;

    /** 超售标记。 */
    private Boolean overbookedFlag;

    /** 接诊医师（含替诊生效后医师）。 */
    private Long practitionerId;

    /** 号源类型（医疗服务）主键。 */
    private Long healthcareServiceId;

    /** 接诊医师展示名。 */
    private String practitionerName;

    /** 号源类型展示名。 */
    private String healthcareServiceName;

    /** 科室（机构）展示名。 */
    private String organizationName;
}
