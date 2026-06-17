/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.dto;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class ReturnToSupplierListRowDto {

    /** 供应请求明细主键。 */
    @Schema(description = "明细主键 id")
    private Long id;
    /** 业务单号。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
    /** 关联来源业务单号。 */
    @Schema(description = "来源单号 originalBusNo")
    private String originalBusNo;
    /** 供应类型持久化码。 */
    @Schema(description = "供应类型 typeEnum")
    private Integer typeEnum;
    /** 单据状态持久化码。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;
    /** 供应类别持久化码。 */
    @Schema(description = "供应类别 categoryEnum")
    private Integer categoryEnum;
    /** 源药库位置主键。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;
    /** 目的位置主键。 */
    @Schema(description = "目的位置 purposeLocationId")
    private Long purposeLocationId;
    /** 供应商主键。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;
    /** 物品定义主键。 */
    @Schema(description = "物品 itemId")
    private Long itemId;
    /** 物品来源表名。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;
    /** 退货数量（拆零量）。 */
    @Schema(description = "退货数量 itemQuantity")
    private BigDecimal itemQuantity;
    /** 计量单位编码。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;
    /** 批号。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;
    /** 追溯码。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;
    /** 单价。 */
    @Schema(description = "单价 price")
    private BigDecimal price;
    /** 行合计金额。 */
    @Schema(description = "行合计 totalPrice")
    private BigDecimal totalPrice;
    /** 申请时间。 */
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;
    /** 审批时间。 */
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;
}
