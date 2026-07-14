/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stockout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药库出库单列表聚合行（IF-WHS-SO-02：每 bus_no 一行）。
 *
 * <p>类型、状态等列为持久化 {@code code}（B.10.8.1）；{@code statusEnumText} 与目的展示名由列表查询后在应用层装配。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库出库单列表聚合行")
public class StockOutListRowDto {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 供应类型持久化码，固定为 CABINET_STOCK_OUT。 */
    @Schema(description = "供应类型持久化码，固定为 CABINET_STOCK_OUT")
    private String typeEnum;

    /** 单据状态持久化码（SupplyStatus#getCode）。 */
    @Schema(description = "单据状态持久化码（SupplyStatus#getCode）")
    private String statusEnum;

    /** 单据状态中文展示（SupplyStatus#getDisplay）。 */
    @Schema(description = "单据状态中文展示（SupplyStatus#getDisplay）")
    private String statusEnumText;

    /** 驳回原因（status=REJECT 时取供应行 reason；其它态可空）。 */
    @Schema(description = "驳回原因")
    private String rejectReason;

    /** 源药库位置主键。 */
    @Schema(description = "源药库位置主键")
    private Long sourceLocationId;

    /** 目的位置主键。 */
    @Schema(description = "目的位置主键")
    private Long purposeLocationId;

    /** 目的位置展示名（到何处）。 */
    @Schema(description = "目的位置展示名（到何处）")
    private String purposeLocationName;

    /** 申请时间（apply_time）。 */
    @Schema(description = "申请时间（apply_time）")
    private LocalDateTime applyTime;

    /** 审批时间。 */
    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;

    /** 制单时间（occurrence_time 聚合）。 */
    @Schema(description = "制单时间（occurrence_time 聚合）")
    private LocalDateTime occurrenceTime;

    /** 申请人主键。 */
    @Schema(description = "申请人主键")
    private Long applicantId;

    /** 制单人展示名。 */
    @Schema(description = "制单人展示名")
    private String creatorName;

    /** 审批人主键。 */
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人展示名。 */
    @Schema(description = "审批人展示名")
    private String approverName;

    /** 单据总金额。 */
    @Schema(description = "单据总金额")
    private BigDecimal totalAmount;

    /** 关联上游业务单号。 */
    @Schema(description = "关联上游业务单号")
    private String originalBusNo;
}
