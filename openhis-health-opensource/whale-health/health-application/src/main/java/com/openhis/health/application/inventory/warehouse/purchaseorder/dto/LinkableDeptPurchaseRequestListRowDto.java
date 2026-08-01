/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可关联科室采购申请列表聚合行（IF-WHS-PO-12：每 {@code bus_no} 一行）。
 *
 * <p>金额与时刻等聚合口径在 Mapper 内计算；{@code creatorName}/{@code approverName}/
 * {@code purposeLocationName}/{@code statusEnumText} 由
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService}
 * 在返回前批量富化。列表锚点为 {@code purposeLocationId}（科室药房）。</p>
 */
@Getter
@Setter
public class LinkableDeptPurchaseRequestListRowDto {

    /** 业务单号（聚合键）。 */
    private String busNo;

    /** 目的科室药房位置主键。 */
    private Long purposeLocationId;

    /** 目的科室药房展示名；主数据未命中时为 null。 */
    private String purposeLocationName;

    /** 单据状态持久化码（头行与各明细行须一致）。 */
    private String statusEnum;

    /** 单据状态展示文案（{@link com.openhis.health.domain.share.enums.SupplyStatus#getDisplay()}）。 */
    private String statusEnumText;

    /** 制单人（申请人）展示名。 */
    private String creatorName;

    /** 审批人主键（头行口径）。 */
    private Long approverId;

    /** 审批人展示名。 */
    private String approverName;

    /** 该单下各明细 {@code total_price} 之和。 */
    private BigDecimal totalAmount;

    /** 审批时间：聚合组内按主键升序首条非空 {@code approval_time}。 */
    private LocalDateTime approvalTime;

    /** 申请时间：组内 {@code apply_time} 最大值。 */
    private LocalDateTime applyTime;

    /** 制单时间：组内各行 {@code occurrence_time} 最大值。 */
    private LocalDateTime occurrenceTime;
}
