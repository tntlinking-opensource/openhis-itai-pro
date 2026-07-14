/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯码报表分页行（DM-SDD §4.6.8）。
 */
@Getter
@Setter
public class TraceNumberReportRowQueryModel {

    /** 追溯码。 */
    private String traceNo;

    /** 药品定义标识。 */
    private Long itemId;

    /** 批号。 */
    private String lotNumber;

    /** 全院在院状态。 */
    private String inHospitalStatusEnum;

    /** 拆零剩余量。 */
    private BigDecimal remainingSplitQuantity;

    /** 追溯码单位。 */
    private String traceUnitCode;

    /** 本库状态：IN_LOCATION / OUT_OF_LOCATION。 */
    private String locationStockStatusEnum;

    /** 本库净拆零量。 */
    private BigDecimal locationNetSplitQuantity;

    /** 最近子表流水时间。 */
    private LocalDateTime lastFlowAt;
}
