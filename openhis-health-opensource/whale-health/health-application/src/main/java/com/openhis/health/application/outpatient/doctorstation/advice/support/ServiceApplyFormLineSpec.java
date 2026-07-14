/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 申请单保存入参：主表一行（对应将创建的一条 {@code ServiceRequest} 草稿）。
 */
@Getter
@Setter
public class ServiceApplyFormLineSpec {

    /** 诊疗活动定义主键 {@code wkf_activity_definition.id}。 */
    private Long activityDefinitionId;

    /** 开立数量；须大于 0。 */
    private BigDecimal quantity;

    /** 计价单位 code（术语 {@code UNIT_CODE} 或产品槽位回落）。 */
    private String unitCode;

    /** 纯备注，落库 {@code wkf_service_request.remark}。 */
    private String remark;

    /** 部位/样本明细；检查/检验类别下由编排层校验至少一条。 */
    private List<ServiceApplyFormDetailItemSpec> detailItems;
}
