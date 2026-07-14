/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 调价项目行读模型（对应 {@code cus_price_adjustment} 一行）。
 */
@Getter
@Setter
public class PriceAdjustmentLineQueryModel {

    /** 主行主键 {@code cus_price_adjustment.id}。 */
    private Long id;

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 项目物理表 {@code item_table}。 */
    private String itemTable;

    /** 项目定义主键 {@code item_id}。 */
    private Long itemId;

    /** 目标进价 {@code new_purchase_amount}。 */
    private BigDecimal newPurchaseAmount;

    /** 目标售价 {@code new_selling_amount}。 */
    private BigDecimal newSellingAmount;

    /** 调价原因 {@code adjustment_reason}。 */
    private String adjustmentReason;

    /** 制单时间 {@code adjustment_time}。 */
    private Date adjustmentTime;

    /** 行状态 code（与同单其他行共享）。 */
    private String statusEnum;

    /** 操作人 {@code operator_id}。 */
    private Long operatorId;

    /** 机构 {@code org_id}。 */
    private Long orgId;

    /** 审批人 {@code approver_id}。 */
    private Long approverId;

    /** 审批/生效时间 {@code approve_time}。 */
    private Date approveTime;

    /**
     * 展示口径：{@code clinical} 诊疗一对一；{@code logistics} 药品耗材批号。
     */
    private String adjustmentStyle;

    /**
     * 行级盈亏合计：Σ子表 {@code purchase_impact} + Σ子表 {@code selling_impact}（列表装配用）。
     */
    private BigDecimal totalImpact;

    /** 批号明细或诊疗一对一调前价行。 */
    private List<PriceAdjustmentDetailQueryModel> details;
}
