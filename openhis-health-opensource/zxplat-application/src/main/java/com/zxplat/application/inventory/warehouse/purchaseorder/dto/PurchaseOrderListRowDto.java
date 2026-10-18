/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * <p>金额与时刻等聚合口径在 Mapper 内计算；{@code supplierName}/{@code creatorName}/{@code approverName}/
 * {@code purposeLocationName}/{@code statusEnumText} 由
 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService}
 * 在返回前批量富化。列表锚点为 {@code purposeLocationId}（订货目的药库）。{@code statusEnum} 为
 * {@link com.zxplat.domain.share.enums.SupplyStatus#getCode()}（B.10.8.1）；{@code statusEnumText} 为同枚举 {@code getDisplay()}。</p>
 */
@Getter
@Setter
public class PurchaseOrderListRowDto {

    /** 业务单号（聚合键）。 */
    private String busNo;

    /**
     * 供应商展示名：由供应商主数据解析，未命中时为 null。
     */
    private String supplierName;

    /**
     * 目的药库位置主键：与同号明细 {@code purpose_location_id} 头字段一致（列表筛选锚点）。
     */
    private Long purposeLocationId;

    /**
     * 目的药库展示名：由 {@link com.zxplat.domain.administration.location.service.LocationQueryService} 解析，未命中时为 null。
     */
    private String purposeLocationName;

    /**
     * 单据状态持久化码（头行与各明细行须一致，否则 Mapper 抛错）。
     */
    private String statusEnum;

    /**
     * 单据状态展示文案：由 {@code statusEnum} 解析 {@link com.zxplat.domain.share.enums.SupplyStatus#getDisplay()}；未知 code 时为 null。
     */
    private String statusEnumText;

    /** 驳回原因（status=REJECT 时取供应行 reason；其它态可空）。 */
    private String rejectReason;

    /**
     * 制单人（申请人）展示名：由申请人主键解析。
     */
    private String creatorName;

    /**
     * 审批人主键（头行口径，与 Mapper 选取的「最新申请」头行一致）；展示名见 {@link #approverName}。
     */
    private Long approverId;

    /**
     * 审批人展示名。
     */
    private String approverName;

    /**
     * 该单下各明细 {@code total_price} 之和（未扣税等调整时即为列表展示金额）。
     */
    private BigDecimal totalAmount;

    /**
     * 审批时间：聚合组内按主键升序首条非空 {@code approval_time}（与 Mapper 稳定口径一致）。
     */
    private LocalDateTime approvalTime;

    /**
     * 申请时间：组内 {@code apply_time} 最大值（提交审批后由服务端写入；草稿阶段可为 null）。
     */
    private LocalDateTime applyTime;

    /**
     * 制单时间：组内各行 {@code occurrence_time} 的最大值（业务制单时刻，由前端随明细传入）。
     */
    private LocalDateTime occurrenceTime;
}
