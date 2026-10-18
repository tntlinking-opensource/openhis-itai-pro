/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pharmacydailysettlement;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 药房日结主区品规汇总行（DD §2.8.1：同药且进/售单价一致才合并；净 0 仍展示）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药房日结品规汇总行")
public class PharmacyDailySettlementProductRowDto {

    /** 药品主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品主键")
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 药品目录业务编码 bus_no。 */
    @Schema(description = "药品目录 bus_no")
    private String busNo;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 生产厂家。 */
    @Schema(description = "生产厂家")
    private String manufacturer;

    /** 净数量（已发 − 已退；一发一退可为 0）。 */
    @Schema(description = "净数量")
    private BigDecimal netQuantity;

    /** 进价单价（分桶键；同桶内一致）。 */
    @Schema(description = "进价单价")
    private BigDecimal purchaseUnitPrice;

    /** 售价单价（分桶键；同桶内一致）。 */
    @Schema(description = "售价单价")
    private BigDecimal saleUnitPrice;

    /** 进价总额（净）。 */
    @Schema(description = "进价总额（净）")
    private BigDecimal purchaseAmount;

    /** 售价总额（净）。 */
    @Schema(description = "售价总额（净）")
    private BigDecimal saleAmount;

    /**
     * 基药标识（药典 {@code basic_flag}）；供品规销售排名按重点药类型筛选。
     */
    @Schema(description = "是否基药")
    private Boolean basicFlag;

    /**
     * 国家重点监控药品标识（药典 {@code national_monitor_flag}）。
     */
    @Schema(description = "是否国家重点监控药品")
    private Boolean nationalMonitorFlag;

    /**
     * 集采药品标识（药典 {@code central_purchase_flag}）。
     */
    @Schema(description = "是否集采药品")
    private Boolean centralPurchaseFlag;

    /**
     * 抗生素标识（药典 {@code antibiotic_flag}）。
     */
    @Schema(description = "是否抗生素")
    private Boolean antibioticFlag;

    /**
     * 药品性质码（药典 {@code pharmacology_category_code}；术语 medicine_properties）。
     *
     * <p>供品规销售排名按麻醉/精一/精二筛选。</p>
     */
    @Schema(description = "药品性质码")
    private String pharmacologyCategoryCode;
}
