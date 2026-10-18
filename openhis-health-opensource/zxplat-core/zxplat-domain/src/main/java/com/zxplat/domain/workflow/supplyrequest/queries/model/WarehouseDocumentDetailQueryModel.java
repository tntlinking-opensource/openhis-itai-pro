/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库房单据明细查询行（查 {@code wkf_supply_request}；替代 {@code WarehouseDocumentDetailReadModel}）。
 */
@Getter
@Setter
public class WarehouseDocumentDetailQueryModel {

    /** 供应请求行主键 {@code wkf_supply_request.id}。 */
    private Long id;
    /** 业务单号（同号多行明细）。 */
    private String busNo;
    /** 来源业务单号（引用上游单据）。 */
    private String originalBusNo;
    /** 供应类型持久化码。 */
    private String typeEnum;
    /** 单据状态持久化码。 */
    private String statusEnum;
    /** 供应类别持久化码。 */
    private String categoryEnum;
    /** 汇总单头主键（汇总类单据）。 */
    private Long summaryId;
    /** 关联配送/出库单主键。 */
    private Long deliverId;
    /** 源位置类型持久化码。 */
    private String sourceTypeEnum;
    /** 源位置主键。 */
    private Long sourceLocationId;
    /** 目标位置类型持久化码。 */
    private String purposeTypeEnum;
    /** 目标位置主键。 */
    private Long purposeLocationId;
    /**
     * 领用科室（行政 Organization 主键；库列 {@code consuming_organization_id}）。
     *
     * <p>{@code DEPT_ISSUE}/{@code DEPT_ISSUE_RETURN} 有值；其它类型通常为空。</p>
     */
    private Long consumingOrganizationId;
    /** 物品来源表名。 */
    private String itemTable;
    /** 物品定义主键。 */
    private Long itemId;
    /** 业务数量（请领/盈亏等，拆零）。 */
    private BigDecimal itemQuantity;
    /** 实盘量等拆零持久化量（盘点/损益类）；报表展示须按 {@link #unitCode} 换算为操作单位。 */
    private BigDecimal totalQuantity;
    /** 计量单位编码。 */
    private String unitCode;
    /** 批号。 */
    private String lotNumber;
    /** 追溯码。 */
    private String traceNo;
    /** 单价（展示/计价辅助）。 */
    private BigDecimal price;
    /** 行金额（数量×单价或实盘金额）。 */
    private BigDecimal totalPrice;
    /** 盈亏金额（盘点/损益类落库值）。 */
    private BigDecimal profitLossPrice;
    /** 供应商主键。 */
    private Long supplierId;
    /** 申请人主键。 */
    private Long applicantId;
    /** 申请时间。 */
    private LocalDateTime applyTime;
    /** 审批人主键。 */
    private Long approverId;
    /** 审批时间。 */
    private LocalDateTime approvalTime;
    /** 经手人 practitioner 主键。 */
    private Long practitionerId;
    /** 业务发生时间。 */
    private LocalDateTime occurrenceTime;
    /** 计划/统计开始时刻（部分单据类型）。 */
    private LocalDateTime startTime;
    /** 计划/统计结束时刻（部分单据类型）。 */
    private LocalDateTime endTime;
    /** 原因说明。 */
    private String reason;
    /** 行备注（库列 {@code remake}）。 */
    private String remake;
}
