/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 追溯码主档贴码单位快照（发药应扫个数换算用；取自 {@code cus_trace_number}，非药典目录）。
 */
@Getter
@Setter
public class TraceUnitSnapshotQueryModel {

    /** 主档贴码单位。 */
    private String traceUnitCode;

    /** 建码时 1 个追溯单位对应的拆零数量快照。 */
    private BigDecimal traceUnitRatio;
}
