/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 调价单列表过滤入参：按 {@code bus_no} 聚合分页（对齐库房 {@link com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery}）。
 *
 * <p>时刻窗映射 {@link #setStartDate} / {@link #setEndDate}；排序键为
 * {@code COALESCE(submit_time, adjustment_time)}，草稿无提交时间时回落制单时间。</p>
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
}
