/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可关联科室入库单列表行（按 {@code bus_no} 聚合，一行一单）。
 */
@Getter
@Setter
public class LinkableStockInListRowDto {

    /** 入库单号 busNo。 */
    @Schema(description = "入库单号 busNo")
    private String busNo;

    /** 目的科室药房 purposeLocationId。 */
    @Schema(description = "目的科室药房 purposeLocationId")
    private Long purposeLocationId;

    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 总金额 totalAmount。 */
    @Schema(description = "总金额 totalAmount")
    private BigDecimal totalAmount;

    /** 审批时间 approvalTime。 */
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 是否仍可退库 returnable。 */
    @Schema(description = "是否仍可退库 returnable")
    private Boolean returnable;
}
