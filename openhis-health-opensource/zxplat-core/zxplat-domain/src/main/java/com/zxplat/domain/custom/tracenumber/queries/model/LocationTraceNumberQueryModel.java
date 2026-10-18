/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 库位关联追溯码读模型（DM-SDD §4.6.4）。
 */
@Getter
@Setter
public class LocationTraceNumberQueryModel {

    /** 追溯码。 */
    private String traceNo;

    /** 药品定义标识。 */
    private Long itemId;

    /** 批号。 */
    private String lotNumber;

    /** 子表聚合净拆零量。 */
    private BigDecimal locationNetSplitQuantity;
}
