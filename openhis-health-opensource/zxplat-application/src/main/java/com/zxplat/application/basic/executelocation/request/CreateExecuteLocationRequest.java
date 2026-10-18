/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.executelocation.request;

import lombok.Data;

/**
 * 创建单条执行规则请求：描述「某开立科室下，某诊疗大类（+ 可选业务类型 + 可选单品）由哪一执行科室、何时执行」。
 */
@Data
public class CreateExecuteLocationRequest {

    /** 开立科室主键（配置页左侧选中科室）。 */
    private Long organizationId;
    /** 诊疗活动定义主键；为空时表示未绑单品（大类默认或业务类型层）。 */
    private Long activityDefinitionId;
    /** 诊疗大类业务码。 */
    private String activityCategoryCode;
    /** 业务类型码（术语 {@code activity_type_code}）；可空。 */
    private String activityTypeCode;
    /** 执行科室主键（规则行内选择）。 */
    private Long executionOrgId;
    /** 执行地点主键。 */
    private Long executionLocationId;
    /** 开始时间字符串，由 Mapper 解析为 {@link java.time.LocalTime}。 */
    private String startTime;
    /** 结束时间字符串。 */
    private String endTime;
    /** 优先级；空则领域侧按默认 1 处理。 */
    private Integer priority;
    /** 展示顺序。 */
    private Integer displayOrder;
    /** 备注。 */
    private String remark;
}
