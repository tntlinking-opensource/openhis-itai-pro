/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 追溯码主档读模型（DM-SDD §4.6.1）。
 */
@Getter
@Setter
public class TraceNumberQueryModel {

    /** 主档标识。 */
    private Long id;

    /** 追溯码。 */
    private String traceNo;

    /** 品项表。 */
    private String itemTable;

    /** 药品定义标识。 */
    private Long itemId;

    /** 批号。 */
    private String lotNumber;

    /** 追溯码单位。 */
    private String traceUnitCode;

    /** 拆零剩余量。 */
    private BigDecimal remainingSplitQuantity;

    /** 在院状态。 */
    private String inHospitalStatusEnum;
}
