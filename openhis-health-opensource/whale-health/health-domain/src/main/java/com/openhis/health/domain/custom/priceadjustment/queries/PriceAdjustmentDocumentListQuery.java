/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 调价单列表过滤入参：按 {@code bus_no} 聚合分页（对齐库房 {@link com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery}）。
 *
 * <p>时刻窗映射 {@link #setStartDate} / {@link #setEndDate}；排序键为
 * {@code COALESCE(submit_time, adjustment_time)}，草稿无提交时间时回落制单时间。
 * 财务审批列表另可用制单人/审批人/品类/审批时刻窗/单号模糊（见字段 JavaDoc）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PriceAdjustmentDocumentListQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 机构主键；非空时等值过滤 {@code org_id}。 */
    private Long orgId;

    /**
     * 可选审批状态；{@link com.openhis.health.domain.share.enums.PriceAdjustmentStatus#getCode()} 持久化串。
     */
    private String statusEnum;

    /**
     * 品类表名；非空时等值过滤 {@code item_table}（物流药/耗；诊疗表名亦可，财务审批页仅药品/耗材两档）。
     */
    private String itemTable;

    /** 制单人（操作人）主键；非空时等值过滤 {@code operator_id}。 */
    private Long operatorId;

    /** 审批人主键；非空时等值过滤 {@code approver_id}。 */
    private Long approverId;

    /**
     * 单号模糊（仅 {@code bus_no} LIKE）；与父类 {@code keyword} 语义相同列，可并存（AND）。
     * <p>财务审批页「单号」映射本字段；keyword 仍可用于兼容旧列表。</p>
     */
    private String busNoLike;

    /** 审批时间窗起（闭区间，映射 {@code approve_time}）。 */
    private LocalDateTime approveTimeFrom;

    /** 审批时间窗止（闭区间，映射 {@code approve_time}）。 */
    private LocalDateTime approveTimeTo;

    /** 制单时间窗起（闭区间，映射 {@code adjustment_time}）；与父类列表 coalesce 窗分列。 */
    private LocalDateTime adjustmentTimeFrom;

    /** 制单时间窗止（闭区间，映射 {@code adjustment_time}）。 */
    private LocalDateTime adjustmentTimeTo;
}
