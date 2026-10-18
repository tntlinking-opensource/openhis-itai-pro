/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 发药报表患者侧净汇总行（仅发放事实数量净额；金额由应用层 ChargeItem enrich）。
 */
@Getter
@Setter
public class MedicationDispenseReportSummaryRow {

    /** 维度键：药房 id / 药品 id / 类型 code 的字符串形态，供前端合并。 */
    private String dimensionKey;

    /** 汇总维度码（与入参一致）。 */
    private String summaryDimension;

    /** 药房主键；维度非药房时可为 null。 */
    private Long locationId;

    /** 药品定义主键；维度非品规时可为 null。 */
    private Long medicationId;

    /** 药品类型 code；维度非类型时可为 null。 */
    private String categoryCode;

    /** 净数量：发药为正、退药为负的代数和。 */
    private BigDecimal netQuantity;

    /** 命中明细行数（含退药行）。 */
    private long lineCount;
}
