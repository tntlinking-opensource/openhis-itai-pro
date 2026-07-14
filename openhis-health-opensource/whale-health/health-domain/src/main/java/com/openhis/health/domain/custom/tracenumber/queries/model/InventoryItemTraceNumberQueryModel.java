/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.queries.model;

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

    /** 本库净拆零量。 */
    private BigDecimal locationNetSplitQuantity;

    /** 追溯码单位。 */
    private String traceUnitCode;

    /** 全院在院状态。 */
    private String inHospitalStatusEnum;
}
