/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 按时间窗与间隔批量加档请求。
 */
@Getter
@Setter
public class BatchAddHcDaySlotsRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 计划日期。 */
    @NotNull
    private LocalDate planDate;

    /** 开放窗开始时刻。 */
    @NotNull
    private LocalTime windowStart;

    /** 开放窗结束时刻。 */
    @NotNull
    private LocalTime windowEnd;

    /** 时段间隔（分钟）。 */
    @NotNull
    @Min(1)
    private Integer slotMinutes;

    /** 合同类型编码列表。 */
    @NotEmpty
    private List<String> contractTypeCodes;

    /**
     * 校区编码列表（校区模块启用时；可多选，各校区独立容量）。
     * 为空时回退 {@link #campusCode}；仍为空则按无校区生成一列。
     */
    private List<String> campusCodes;

    /**
     * 各校区容量；键为校区编码。未配置的校区使用 {@link #capacity}。
     */
    private Map<String, Integer> capacityByCampus;

    /** 单校区兼容字段；优先使用 {@link #campusCodes}。 */
    private String campusCode;

    /** 默认容量（各校区未单独配置时使用，默认 0）。 */
    private Integer capacity;

    /** 是否开放（默认 true）。 */
    private Boolean openFlag;
}
