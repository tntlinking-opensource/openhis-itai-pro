/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 单据详情 Excel 导出行（并集稀疏列）。
 *
 * <p>纯数量/金额保留 {@link BigDecimal}，由 {@code TabularExcelExporter} 写成数字单元格；
 * 包装/最小/操作数量与各自单位分列（禁止两数量共用一个单位列或仅合并「数量+单位」文案）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情导出行")
public class DocumentDetailExportRowDto {

    /** 单据号。 */
    @Schema(description = "单据号")
    private String busNo;

    /** 来源单号。 */
    @Schema(description = "来源单号")
    private String originalBusNo;

    /** 单据类型。 */
    @Schema(description = "单据类型")
    private String typeEnumText;

    /** 源库位。 */
    @Schema(description = "源库位")
    private String sourceLocationName;

    /** 目的库位。 */
    @Schema(description = "目的库位")
    private String purposeLocationName;

    /** 领用科室（行政 Organization 名称；领用/退领有值）。 */
    @Schema(description = "领用科室")
    private String consumingOrganizationName;

    /** 供应商。 */
    @Schema(description = "供应商")
    private String supplierName;

    /** 申请人。 */
    @Schema(description = "申请人")
    private String applicantName;

    /** 审批人。 */
    @Schema(description = "审批人")
    private String approverName;

    /** 经手人。 */
    @Schema(description = "经手人")
    private String practitionerName;

    /** 申请时间。 */
    @Schema(description = "申请时间")
    private String applyTime;

    /** 审批时间。 */
    @Schema(description = "审批时间")
    private String approvalTime;

    /** 品名。 */
    @Schema(description = "品名")
    private String productName;

    /** 编码。 */
    @Schema(description = "编码")
    private String productBusNo;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 厂家。 */
    @Schema(description = "厂家")
    private String manufacturer;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 操作单位数量（整数导出）。 */
    @Schema(description = "操作单位数量")
    private BigDecimal itemQuantity;

    /** 操作单位名称（对应操作单位数量）。 */
    @Schema(description = "操作单位")
    private String unitCodeDictText;

    /** 包装单位数量（整数导出，可求和）。 */
    @Schema(description = "包装单位数量")
    private BigDecimal packageQuantity;

    /** 包装单位名称（对应包装单位数量）。 */
    @Schema(description = "包装单位")
    private String packageUnitName;

    /** 最小单位数量（整数导出，可求和）。 */
    @Schema(description = "最小单位数量")
    private BigDecimal minUnitQuantity;

    /** 最小单位名称（对应最小单位数量）。 */
    @Schema(description = "最小单位")
    private String minUnitName;

    /** 单价（展示 2 位）。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 金额（展示 2 位）。 */
    @Schema(description = "金额")
    private BigDecimal totalPrice;

    /** 账面数量（整数导出）。 */
    @Schema(description = "账面数量")
    private BigDecimal bookQuantity;

    /** 实盘数量（整数导出）。 */
    @Schema(description = "实盘数量")
    private BigDecimal actualQuantity;

    /** 盈亏数量（整数导出）。 */
    @Schema(description = "盈亏数量")
    private BigDecimal gainLossQuantity;

    /** 实盘金额（展示 2 位）。 */
    @Schema(description = "实盘金额")
    private BigDecimal actualAmount;

    /** 盈亏金额（展示 2 位）。 */
    @Schema(description = "盈亏金额")
    private BigDecimal gainLossAmount;

    /** 备注。 */
    @Schema(description = "备注")
    private String remake;
}
