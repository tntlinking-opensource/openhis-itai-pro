/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 保存团检批次请求（6.x 旧版 API 别名，对应 SaveGroupCheckupBatchRequest）。
 */
@Getter
@Setter
public class SaveGroupBatchRequest {

    /** 团检批次主键（更新时传入）。 */
    private Long id;

    /** 团检单位主键。 */
    private Long groupOrgId;

    /** 团检单位名称。 */
    private String groupOrgName;

    /** 批次名称。 */
    @NotBlank
    private String batchName;

    /** 预约日期。 */
    private LocalDate bookingDate;

    /** 号源开始时刻。 */
    private LocalTime slotStartTime;

    /** 容量。 */
    private Integer capacity;

    /** 已约人数。 */
    private Integer bookedCount;

    /** 套餐名称。 */
    private String packageName;

    /** 体检套餐主键。 */
    private Long packageId;

    /** 日程主键。 */
    private Long scheduleId;
}
