/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.medicationdispense.outpatient.dto;

import com.zxplat.application.report.medicationdispense.support.MedicationDispenseReportPricingEnricher;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门诊发药报表汇总行 DTO。
 */
@Data
@Schema(description = "门诊发药报表汇总行")
public class OutpatientMedicationDispenseSummaryRowDto
    implements MedicationDispenseReportPricingEnricher.MedicationDispensePurchaseSaleSummaryLine {

    /** 维度键（与领域分桶键一致，供前端合并）。 */
    @Schema(description = "维度键")
    private String dimensionKey;

    /** 汇总维度 code。 */
    @Schema(description = "汇总维度 code")
    private String summaryDimension;

    /** 维度展示名称（药房名/品规名/类型文案/科室名）。 */
    @Schema(description = "维度名称")
    private String dimensionName;

    /** 药房主键；非药房维可为 null。 */
    @Schema(description = "药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 药品定义主键；非品规维可为 null。 */
    @Schema(description = "药品主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品类型 code；非类型维可为 null。 */
    @Schema(description = "药品类型 code")
    private String categoryCode;

    /** 领用科室组织主键；非科室维可为 null。 */
    @Schema(description = "科室组织主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 净数量（发/领为正，退为负）。 */
    @Schema(description = "净数量")
    private BigDecimal netQuantity;

    /** 药品编码（品规维）；其它维为空。 */
    @Schema(description = "药品编码")
    private String medicationCode;

    /**
     * 最小单位净数量：品规维按包装倍数将 {@code netQuantity} 换算为拆零量；
     * 无倍数或非品规维时与 {@code netQuantity} 同值或为空。
     */
    @Schema(description = "最小单位净数量")
    private BigDecimal minUnitQuantity;

    /** 包装单位展示名（品规维；如盒、克）；供「数量」列拼接。 */
    @Schema(description = "包装单位名称")
    private String packageUnitName;

    /** 最小单位展示名（品规维；如片、克）；供「最小数量」列拼接。 */
    @Schema(description = "最小单位名称")
    private String minUnitName;

    /** 进价单价（批号进价同明细 enrich；同桶多批号不一致则为空）。 */
    @Schema(description = "进价单价")
    private BigDecimal purchaseUnitPrice;

    /** 售价单价（账单/批号售价同明细 enrich；同桶不一致则为空）。 */
    @Schema(description = "售价单价")
    private BigDecimal saleUnitPrice;

    /** 进价净额（结算 2 位）。 */
    @Schema(description = "进价净额")
    private BigDecimal purchaseNetAmount;

    /** 售价净额（结算 2 位）。 */
    @Schema(description = "售价净额")
    private BigDecimal saleNetAmount;
}
