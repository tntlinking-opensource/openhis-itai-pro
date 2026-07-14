/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.executelocation.request;

import lombok.Data;

import java.util.List;

/**
 * 在同一执行科室与诊疗大类下，批量将多条诊疗定义绑定为相同执行时段/地点/优先级的规则。
 */
@Data
public class BatchCreateExecuteLocationRequest {

    /** 执行科室主键。 */
    private Long executionOrgId;
    /** 诊疗大类业务码。 */
    private String activityCategoryCode;
    /** 共用执行地点主键。 */
    private Long executionLocationId;
    /** 待绑定诊疗定义主键列表，非空。 */
    private List<Long> activityDefinitionIds;
    /** 共用开始时间字符串。 */
    private String startTime;
    /** 共用结束时间字符串。 */
    private String endTime;
    /** 共用优先级。 */
    private Integer priority;
    /** 共用展示顺序。 */
    private Integer displayOrder;
    /** 共用备注。 */
    private String remark;
}
