/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityexecutionlocation.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 批量命中规则查询模型。
 */
@Getter
@Setter
public class ExecutionRuleBatchHitQueryModel {
    /**
     * 输入锚点标识。
     */
    private Long anchorId;
    /**
     * 命中规则主键。
     */
    private Long ruleId;
    /**
     * 执行科室标识。
     */
    private Long executionOrgId;
    /**
     * 执行位置标识。
     */
    private Long executionLocationId;
    /**
     * 优先级。
     */
    private Integer priority;
}
