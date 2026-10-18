/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries.model;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 耗材使用报表患者侧净汇总行（数量净额；金额由应用层 ChargeItem enrich）。
 */
@Getter
@Setter
public class ConsumableUsageDispenseSummaryRow {

    /** 维度键字符串，供前端合并。 */
    private String dimensionKey;

    /** 汇总维度码。 */
    private String summaryDimension;

    /** 患者主键；非患者维可为 null。 */
    private Long patientId;

    /** 科室组织主键；非科室维可为 null。 */
    private Long organizationId;

    /** 库位主键；非库位维可为 null。 */
    private Long locationId;

    /** 耗材定义主键；非品规维可为 null。 */
    private Long deviceDefId;

    /** 净数量：发放为正、退药为负。 */
    private BigDecimal netQuantity;

    /** 命中明细行数。 */
    private long lineCount;
}
