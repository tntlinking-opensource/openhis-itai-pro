/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 护士站执行前：单库位+药品的待发预占汇总行（DRAFT/PREPARATION）。
 */
@Getter
@Setter
public class DispenseExeReserveRowModel {
    /** 药房库位主键。 */
    private Long locationId;
    /** 药品定义主键。 */
    private Long medicationId;
    /** 待发预占总量。 */
    private BigDecimal reserveQuantity;
}
