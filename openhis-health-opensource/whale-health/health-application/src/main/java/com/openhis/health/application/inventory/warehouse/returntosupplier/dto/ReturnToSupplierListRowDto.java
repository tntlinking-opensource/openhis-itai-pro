/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药库退供应商单列表行（IF-WHS-RT-02：按 {@code bus_no} 聚合展示，兼容明细行级遗留路径）。
 *
 * <p>聚合路径一行一单，{@code totalPrice} 为整单合计；明细行级字段（{@code itemId} 等）在聚合路径可为空。</p>
 */
@Getter
@Setter
@Schema(description = "退供应商单列表行 IF-WHS-RT-02")
public class ReturnToSupplierListRowDto {

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

    /** 源药库 sourceLocationId。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;

    /** 目的位置 purposeLocationId。 */
    @Schema(description = "目的位置 purposeLocationId")
    private Long purposeLocationId;

    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 物品 itemId。 */
    @Schema(description = "物品 itemId")
    private Long itemId;

    /** 物品表 itemTable。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;

    /** 退货数量 itemQuantity。 */
    @Schema(description = "退货数量 itemQuantity")
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

    /**
     * 行合计金额：聚合列表路径下为整单明细合计；明细行级路径下为行合计（落库快照）。
     */
    @Schema(description = "金额 totalPrice")
    private BigDecimal totalPrice;

    /** 申请时间 applyTime。 */
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批时间 approvalTime。 */
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;
}
