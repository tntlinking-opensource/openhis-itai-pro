/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * IF-IP-ARG-08：滚费定价解析结果。
 */
@Getter
@Setter
public class AutoRollChargeLineResolved {

    /** 是否解析成功。 */
    private boolean resolved;

    /** 收费项定价主键。 */
    private Long chargeDefinitionId;

    /** 诊疗活动定义主键。 */
    private Long activityId;

    /** 数量单位持久化码。 */
    private String unitCode;

    /** 单价。 */
    private BigDecimal unitPrice;

    /** 行总价（单价 × 数量）。 */
    private BigDecimal totalPrice;
}
