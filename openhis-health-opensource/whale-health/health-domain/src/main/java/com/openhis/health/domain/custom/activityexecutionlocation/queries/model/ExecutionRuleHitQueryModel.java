/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityexecutionlocation.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 单条命中规则查询模型。
 */
@Getter
@Setter
public class ExecutionRuleHitQueryModel {
    /**
     * 规则主键。
     */
    private Long ruleId;
    /**
     * 锚点标识（诊疗定义或收费项定义）。
     */
    private Long anchorId;
    /**
     * 执行科室标识。
     */
    private Long executionOrgId;
    /**
     * 执行位置标识。
     */
    private Long executionLocationId;
    /**
     * 活动类别编码。
     */
    private String activityCategoryCode;
    /**
     * 生效开始时间。
     */
    private LocalTime startTime;
    /**
     * 生效结束时间。
     */
    private LocalTime endTime;
    /**
     * 优先级。
     */
    private Integer priority;
}
