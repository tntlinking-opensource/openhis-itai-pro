/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药库入库单列表聚合行（IF-WHS-IN-02）。
 *
 * <p>金额与时点等聚合口径在 Mapper 内计算；{@code supplierName}/{@code creatorName}/{@code approverName}/
 * {@code purposeLocationName}/{@code statusEnumText} 由
 * {@link com.zxplat.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService}
 * 在返回前批量富化。{@code statusEnum} 为 {@link com.zxplat.domain.share.enums.SupplyStatus#getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库入库单列表聚合行")
public class InboundReceiptListRowDto {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 供应商展示名。 */
    @Schema(description = "供应商展示名")
    private String supplierName;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 目的库房位置主键。 */
    @Schema(description = "目的库房位置主键")
    private Long purposeLocationId;

    /** 目的库房展示名。 */
    @Schema(description = "目的库房展示名")
    private String purposeLocationName;

    /** 单据状态 SupplyStatus#getCode。 */
    @Schema(description = "单据状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 单据状态展示文案。 */
    @Schema(description = "单据状态展示文案")
    private String statusEnumText;

    /** 驳回原因（status=REJECT 时取供应行 reason；其它态可空）。 */
    @Schema(description = "驳回原因")
    private String rejectReason;

    /** 制单人展示名。 */
    @Schema(description = "制单人展示名")
    private String creatorName;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 审批人主键。 */
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人展示名。 */
    @Schema(description = "审批人展示名")
    private String approverName;

    /** 单据总金额。 */
    @Schema(description = "单据总金额")
    private BigDecimal totalAmount;

    /** 审批时间。 */
    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;

    /** 申请时间。 */
    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    /** 制单时间。 */
    @Schema(description = "制单时间")
    private LocalDateTime occurrenceTime;
}
