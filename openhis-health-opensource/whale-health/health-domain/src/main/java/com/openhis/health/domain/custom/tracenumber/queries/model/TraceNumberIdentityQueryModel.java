/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 批量扫码身份反查读模型（DM-SDD §4.6.6）。
 */
@Getter
@Setter
public class TraceNumberIdentityQueryModel {

    /** 入参追溯码（规范化后）。 */
    private String traceNo;

    /** 解析状态：OK / NOT_FOUND / NOT_IN_HOSPITAL / DUPLICATE_IN_REQUEST。 */
    private String resolveStatusEnum;

    /** 品项表。 */
    private String itemTable;

    /** 药品定义标识。 */
    private Long itemId;

    /** 批号。 */
    private String lotNumber;

    /** 追溯码单位。 */
    private String traceUnitCode;

    /**
     * 建码时 1 个追溯单位对应的拆零数量快照（主档 {@code trace_unit_ratio}）。
     */
    private BigDecimal traceUnitRatio;

    /** 拆零剩余量。 */
    private BigDecimal remainingSplitQuantity;

    /** 在院状态。 */
    private String inHospitalStatusEnum;
}
