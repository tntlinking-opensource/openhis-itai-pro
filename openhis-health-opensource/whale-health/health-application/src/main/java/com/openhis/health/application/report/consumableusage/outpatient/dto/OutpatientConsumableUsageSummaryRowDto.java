/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportAmountEnricher;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊耗材使用报表汇总行（IF-RPT-OCU-02）。
 */
@Data
@Schema(description = "门诊耗材使用报表汇总行")
public class OutpatientConsumableUsageSummaryRowDto
    implements ConsumableUsageReportAmountEnricher.ConsumableUsagePurchaseSaleSummaryLine {

    /** 维度键（前端合并用）。 */
    @Schema(description = "维度键")
    private String dimensionKey;

    /** 汇总维度码。 */
    @Schema(description = "汇总维度码")
    private String summaryDimension;

    /** 维度展示名（耗材房名/品规名/类型文案/科室名）。 */
    @Schema(description = "维度名称")
    private String dimensionName;

    /** 患者主键；非患者维可空（遗留 PATIENT 维兼容）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 科室组织主键；非科室维可空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室组织主键")
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 库位主键；非库位维可空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库位主键")
    private Long locationId;

    /** 库位名称。 */
    @Schema(description = "库位名称")
    private String locationName;

    /** 耗材定义主键；非品规维可空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材定义主键")
    private Long deviceDefId;

    /** 耗材名称。 */
    @Schema(description = "耗材名称")
    private String deviceName;

    /** 耗材类型 code（CATEGORY 维；术语 device_category_code）。 */
    @Schema(description = "耗材类型 code")
    private String categoryCode;

    /** 净数量（发/领为正、退为负）。 */
    @Schema(description = "净数量")
    private BigDecimal netQuantity;

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
