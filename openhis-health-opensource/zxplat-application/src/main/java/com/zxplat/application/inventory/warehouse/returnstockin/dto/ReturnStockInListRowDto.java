/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退库入库壳单列表行（IF-WHS-RSI-02：按 {@code bus_no} 聚合，一行一单）。
 *
 * <p>类型固定为 type 5（{@code CABINET_RETURN_STOCK_IN}）；{@code originalBusNo} 指向下游退库单号；
 * 整单合计写 {@code totalAmount}；{@code supplierName}/{@code creatorName}/{@code approverName} 由应用层列表查询后批量富化。</p>
 */
@Getter
@Setter
@Schema(description = "退库入库壳单列表行 IF-WHS-RSI-02")
public class ReturnStockInListRowDto {

    /** 明细主键 id。 */
    @Schema(description = "明细主键 id")
    private Long id;

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 来源单号 originalBusNo。 */
    @Schema(description = "来源单号 originalBusNo")
    private String originalBusNo;

    /** 供应类型 typeEnum。 */
    @Schema(description = "供应类型 typeEnum")
    private Integer typeEnum;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;

    /** 供应类别 categoryEnum。 */
    @Schema(description = "供应类别 categoryEnum")
    private Integer categoryEnum;

    /** 源位置 sourceLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源位置 sourceLocationId")
    private Long sourceLocationId;

    /** 目的药库 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的药库 purposeLocationId")
    private Long purposeLocationId;

    /** 供应商 supplierId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 供应商展示名（列表富化）。 */
    @Schema(description = "供应商展示名")
    private String supplierName;

    /** 物品 itemId。 */
    @Schema(description = "物品 itemId")
    private Long itemId;

    /** 物品表 itemTable。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;

    /** 退库数量 itemQuantity。 */
    @Schema(description = "退库数量 itemQuantity")
    private BigDecimal itemQuantity;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 追溯码 traceNo。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 单据总金额（聚合列表侧栏；一行一单）。 */
    @Schema(description = "单据总金额")
    private BigDecimal totalAmount;

    /** 申请人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请人主键")
    private Long applicantId;

    /** 制单人展示名（列表富化）。 */
    @Schema(description = "制单人展示名")
    private String creatorName;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人展示名（列表富化）。 */
    @Schema(description = "审批人展示名")
    private String approverName;

    /** 申请时间 applyTime。 */
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批时间 approvalTime。 */
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;
}
