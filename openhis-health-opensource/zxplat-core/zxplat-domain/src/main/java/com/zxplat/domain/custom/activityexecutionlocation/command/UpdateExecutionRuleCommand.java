/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityexecutionlocation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 更新执行科室规则命令。
 */
@Getter
@Setter
public class UpdateExecutionRuleCommand {
    /**
     * 规则主键。
     */
    private Long id;
    /**
     * 开立科室标识。
     */
    private Long organizationId;
    /**
     * 诊疗定义标识；空表示未绑单品。
     */
    private Long activityDefinitionId;
    /**
     * 业务类型码（术语 {@code activity_type_code}）；可空，更新时允许清空以回到大类默认。
     */
    private String activityTypeCode;
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
    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 操作人姓名。
     */
    private String operatorName;
}
