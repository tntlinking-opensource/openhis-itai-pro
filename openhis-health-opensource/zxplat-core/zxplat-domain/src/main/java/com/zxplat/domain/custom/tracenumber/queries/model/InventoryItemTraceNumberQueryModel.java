/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 库存行本库仍在库码读模型（DM-SDD §4.6.7）。
 */
@Getter
@Setter
public class InventoryItemTraceNumberQueryModel {

    /** 追溯码。 */
    private String traceNo;

    /**
     * 批号（主档 {@code lot_number}）。
     *
     * <p>全院按药品浏览时用于前端按批号分组；本库按库存行查询时与入参批号一致。</p>
     */
    private String lotNumber;

    /** 本库净拆零量（子表按库位轧差；全院在院列表场景可空）。 */
    private BigDecimal locationNetSplitQuantity;

    /**
     * 主档拆零剩余量（{@code cus_trace_number.remaining_split_quantity}）。
     *
     * <p>单位为药典拆零单位；全院在院浏览以此为准，与本库净量字段语义分离。</p>
     */
    private BigDecimal remainingSplitQuantity;

    /** 追溯码单位（主档 {@code cus_trace_number.trace_unit_code}）。 */
    private String traceUnitCode;

    /**
     * 建码时 1 个追溯单位对应的拆零数量快照（主档 {@code cus_trace_number.trace_unit_ratio}）。
     *
     * <p>发药应扫个数换算优先本列；禁止回落药典目录比。</p>
     */
    private BigDecimal traceUnitRatio;

    /** 全院在院状态。 */
    private String inHospitalStatusEnum;
}
