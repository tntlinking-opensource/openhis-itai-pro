/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 单据详情 Excel 导出行（并集稀疏列字符串化）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情导出行")
public class DocumentDetailExportRowDto {

    @Schema(description = "单据号")
    /** 单据号。 */
    private String busNo;
    @Schema(description = "来源单号")
    /** 来源单号。 */
    private String originalBusNo;
    @Schema(description = "单据类型")
    /** 源库位。 */
    private String typeEnumText;
    @Schema(description = "源库位")
    private String sourceLocationName;
    @Schema(description = "目的库位")
    /** 目的库位。 */
    private String purposeLocationName;
    @Schema(description = "领用科室")
    /** 领用科室（行政 Organization 名称；领用/退领有值）。 */
    private String consumingOrganizationName;
    @Schema(description = "供应商")
    /** 供应商。 */
    private String supplierName;
    @Schema(description = "申请人")
    /** 审批人。 */
    private String applicantName;
    @Schema(description = "审批人")
    private String approverName;
    @Schema(description = "经手人")
    /** 经手人。 */
    private String practitionerName;
    @Schema(description = "申请时间")
    /** 申请时间。 */
    private String applyTime;
    @Schema(description = "审批时间")
    /** 品名。 */
    private String approvalTime;
    @Schema(description = "品名")
    private String productName;
    @Schema(description = "编码")
    /** 编码。 */
    private String productBusNo;
    @Schema(description = "规格")
    /** 规格。 */
    private String specification;
    @Schema(description = "厂家")
    /** 批号。 */
    private String manufacturer;
    @Schema(description = "批号")
    private String lotNumber;
    @Schema(description = "单位")
    /** 单位。 */
    private String unitCodeDictText;
    @Schema(description = "数量")
    /** 数量。 */
    private String itemQuantity;
    @Schema(description = "单价")
    /** 金额。 */
    private String price;
    @Schema(description = "金额")
    private String totalPrice;
    @Schema(description = "账面数量")
    /** 账面数量。 */
    private String bookQuantity;
    @Schema(description = "实盘数量")
    /** 实盘数量。 */
    private String actualQuantity;
    @Schema(description = "盈亏数量")
    /** 实盘金额。 */
    private String gainLossQuantity;
    @Schema(description = "实盘金额")
    private String actualAmount;
    @Schema(description = "盈亏金额")
    /** 盈亏金额。 */
    private String gainLossAmount;
    @Schema(description = "备注")
    /** 备注。 */
    private String remake;
}
