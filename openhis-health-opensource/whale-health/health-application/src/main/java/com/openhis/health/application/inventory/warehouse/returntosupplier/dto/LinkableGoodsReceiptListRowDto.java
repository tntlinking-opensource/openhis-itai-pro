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
 * 可关联进货单列表行（IF-WHS-RT-12：按 {@code bus_no} 聚合，一行一单）。
 */
@Getter
@Setter
@Schema(description = "可关联进货单列表行")
public class LinkableGoodsReceiptListRowDto {

    /** 进货单号 busNo。 */
    @Schema(description = "进货单号 busNo")
    private String busNo;

    /** 目的药库 purposeLocationId。 */
    @Schema(description = "目的药库 purposeLocationId")
    private Long purposeLocationId;

    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 供应商名称 supplierName。 */
    @Schema(description = "供应商名称 supplierName")
    private String supplierName;

    /** 总金额 totalAmount。 */
    @Schema(description = "总金额 totalAmount")
    private BigDecimal totalAmount;

    /** 审批时间 approvalTime。 */
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 制单时间 applyTime。 */
    @Schema(description = "制单时间 applyTime")
    private LocalDateTime applyTime;

    /** 是否仍可退货 returnable。 */
    @Schema(description = "是否仍可退货 returnable")
    private Boolean returnable;
}
