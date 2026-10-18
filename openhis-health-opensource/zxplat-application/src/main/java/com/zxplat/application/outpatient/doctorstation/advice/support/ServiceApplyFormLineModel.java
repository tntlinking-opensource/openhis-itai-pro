/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 申请单列表读模型：单条 {@code ServiceRequest} 行摘要。
 */
@Getter
@Setter
public class ServiceApplyFormLineModel {

    /** 服务申请主键 {@code wkf_service_request.id}。 */
    private Long requestId;

    /** 诊疗活动定义主键。 */
    private Long activityDefinitionId;

    /** 诊疗活动展示名。 */
    private String activityName;

    /** 开立数量。 */
    private BigDecimal quantity;

    /** 单位 code。 */
    private String unitCode;

    /** 单位展示名。 */
    private String unitName;

    /** 纯备注（主表字段）。 */
    private String remark;

    /** 医嘱优先级 code（加急 {@code EM}）。 */
    private String priorityEnum;

    /** 部位/样本明细摘要列表。 */
    private List<ServiceRequestDetailSummaryModel> details;
}
