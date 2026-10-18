/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单据详情报表明细行（并集稀疏列）。
 *
 * <p>数量口径：库内拆零持久；列表同时给出操作单位数量（与单价同口径）、包装单位数量、最小单位数量。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情报表明细行")
public class DocumentDetailRowDto {

    @Schema(description = "单据号 busNo")
    /** 单据号 busNo。 */
    private String busNo;

    @Schema(description = "来源单号 originalBusNo")
    /** 来源单号 originalBusNo。 */
    private String originalBusNo;

    @Schema(description = "单据类型 typeEnum")
    /** 单据类型 typeEnum。 */
    private String typeEnum;

    @Schema(description = "单据类型展示 typeEnumText")
    /** 单据类型展示 typeEnumText。 */
    private String typeEnumText;

    @Schema(description = "状态 statusEnum")
    /** 状态 statusEnum。 */
    private String statusEnum;

    @Schema(description = "状态展示 statusEnumText")
    /** 状态展示 statusEnumText。 */
    private String statusEnumText;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源库位 sourceLocationId")
    /** 源库位 sourceLocationId。 */
    private Long sourceLocationId;

    @Schema(description = "源库位名称 sourceLocationName")
    /** 源库位名称 sourceLocationName。 */
    private String sourceLocationName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的库位 purposeLocationId")
    /** 目的库位 purposeLocationId。 */
    private Long purposeLocationId;

    @Schema(description = "目的库位名称 purposeLocationName")
    /** 目的库位名称 purposeLocationName。 */
    private String purposeLocationName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "领用科室 consumingOrganizationId")
    /** 领用科室（行政 Organization；领用/退领有值）。 */
    private Long consumingOrganizationId;

    @Schema(description = "领用科室名称 consumingOrganizationName")
    /** 领用科室展示名。 */
    private String consumingOrganizationName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商 supplierId")
    /** 供应商 supplierId。 */
    private Long supplierId;

    @Schema(description = "供应商名称 supplierName")
    /** 供应商名称 supplierName。 */
    private String supplierName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请人 applicantId")
    /** 申请人 applicantId。 */
    private Long applicantId;

    @Schema(description = "申请人 applicantName")
    /** 申请人 applicantName。 */
    private String applicantName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人 approverId")
    /** 审批人 approverId。 */
    private Long approverId;

    @Schema(description = "审批人 approverName")
    /** 审批人 approverName。 */
    private String approverName;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人 practitionerId")
    /** 经手人 practitionerId。 */
    private Long practitionerId;

    @Schema(description = "经手人 practitionerName")
    /** 经手人 practitionerName。 */
    private String practitionerName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "申请时间 applyTime")
    /** 申请时间 applyTime。 */
    private LocalDateTime applyTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间 approvalTime")
    /** 审批时间 approvalTime。 */
    private LocalDateTime approvalTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "制单/业务时点 occurrenceTime")
    /** 制单/业务时点 occurrenceTime。 */
    private LocalDateTime occurrenceTime;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "明细行主键 lineId")
    /** 明细行主键 lineId。 */
    private Long lineId;

    @Schema(description = "物品表 itemTable")
    /** 物品表 itemTable。 */
    private String itemTable;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品 itemId")
    /** 物品 itemId。 */
    private Long itemId;

    @Schema(description = "品名 productName")
    /** 品名 productName。 */
    private String productName;

    @Schema(description = "院内编码 productBusNo")
    /** 院内编码 productBusNo。 */
    private String productBusNo;

    @Schema(description = "规格 specification")
    /** 规格 specification。 */
    private String specification;

    @Schema(description = "厂家 manufacturer")
    /** 厂家 manufacturer。 */
    private String manufacturer;

    @Schema(description = "批号 lotNumber")
    /** 批号 lotNumber。 */
    private String lotNumber;

    @Schema(description = "追溯码 traceNo")
    /** 追溯码 traceNo。 */
    private String traceNo;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "效期起 startTime")
    /** 效期起 startTime。 */
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "效期止 endTime")
    /** 效期止 endTime。 */
    private LocalDateTime endTime;

    @Schema(description = "操作单位 unitCode")
    /** 操作单位 unitCode。 */
    private String unitCode;

    @Schema(description = "单位展示 unitCodeDictText")
    /** 单位展示 unitCodeDictText。 */
    private String unitCodeDictText;

    @Schema(description = "操作单位数量 itemQuantity")
    /** 操作单位数量 itemQuantity。 */
    private BigDecimal itemQuantity;

    @Schema(description = "包装单位数量 packageQuantity")
    /** 包装单位数量：拆零量按包装倍数换算。 */
    private BigDecimal packageQuantity;

    @Schema(description = "包装单位名称 packageUnitName")
    /** 包装单位展示名（如盒）。 */
    private String packageUnitName;

    @Schema(description = "最小单位数量 minUnitQuantity")
    /** 最小（拆零）单位数量：库内持久化拆零量。 */
    private BigDecimal minUnitQuantity;

    @Schema(description = "最小单位名称 minUnitName")
    /** 最小单位展示名（如片、克）。 */
    private String minUnitName;

    @Schema(description = "单价 price")
    /** 单价 price。 */
    private BigDecimal price;

    @Schema(description = "行总价 totalPrice")
    /** 行总价 totalPrice。 */
    private BigDecimal totalPrice;

    @Schema(description = "行备注 remake")
    /** 行备注 remake。 */
    private String remake;

    @Schema(description = "账面数量 bookQuantity")
    /** 账面数量 bookQuantity。 */
    private BigDecimal bookQuantity;

    @Schema(description = "实盘数量 actualQuantity")
    /** 实盘数量 actualQuantity。 */
    private BigDecimal actualQuantity;

    @Schema(description = "盈亏数量 gainLossQuantity")
    /** 盈亏数量 gainLossQuantity。 */
    private BigDecimal gainLossQuantity;

    @Schema(description = "实盘金额 actualAmount")
    /** 实盘金额 actualAmount。 */
    private BigDecimal actualAmount;

    @Schema(description = "盈亏金额 gainLossAmount")
    /** 盈亏金额 gainLossAmount。 */
    private BigDecimal gainLossAmount;

    /** 盈亏金额 gainLossAmount。 */
    @JsonIgnore
    private BigDecimal splitItemQuantity;

    /** 拆零实盘量（换算前）。 */
    @JsonIgnore
    private BigDecimal splitTotalQuantity;

    /** 拆零账面量（换算前）。 */
    @JsonIgnore
    private BigDecimal splitBookQuantity;
}
