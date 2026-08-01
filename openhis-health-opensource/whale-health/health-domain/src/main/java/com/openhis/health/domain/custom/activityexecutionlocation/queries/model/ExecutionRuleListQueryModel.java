/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityexecutionlocation.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 执行规则列表查询模型。
 */
@Getter
@Setter
public class ExecutionRuleListQueryModel {
    /**
     * 规则主键。
     */
    private Long id;
    /**
     * 开立科室标识。
     */
    private Long organizationId;
    /**
     * 诊疗定义标识。
     */
    private Long activityDefinitionId;
    /**
     * 活动类别编码。
     */
    private String activityCategoryCode;
    /**
     * 执行科室标识。
     */
    private Long executionOrgId;
    /**
     * 执行位置标识。
     */
    private Long executionLocationId;
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
    /**
     * 展示顺序。
     */
    private Integer displayOrder;
    /**
     * 备注。
     */
    private String remark;
}
