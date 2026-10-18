/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.executelocation.request;

import lombok.Data;

/**
 * 更新执行规则请求：字段与 {@link CreateExecuteLocationRequest} 对齐并增加规则主键（含可选业务类型）。
 */
@Data
public class UpdateExecuteLocationRequest {

    /** 执行规则主键。 */
    private Long id;
    /** 开立科室主键。 */
    private Long organizationId;
    /** 诊疗活动定义主键；空表示未绑单品。 */
    private Long activityDefinitionId;
    /** 诊疗大类业务码。 */
    private String activityCategoryCode;
    /** 业务类型码（术语 {@code activity_type_code}）；可空，允许清空。 */
    private String activityTypeCode;
    /** 执行科室主键。 */
    private Long executionOrgId;
    /** 执行地点主键。 */
    private Long executionLocationId;
    /** 开始时间字符串。 */
    private String startTime;
    /** 结束时间字符串。 */
    private String endTime;
    /** 优先级。 */
    private Integer priority;
    /** 展示顺序。 */
    private Integer displayOrder;
    /** 备注。 */
    private String remark;
}
