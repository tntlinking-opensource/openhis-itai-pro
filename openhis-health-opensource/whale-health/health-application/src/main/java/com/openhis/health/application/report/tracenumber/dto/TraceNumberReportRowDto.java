/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IF-TRC-RPT-02 列表行 DTO。
 */
@Getter
@Setter
public class TraceNumberReportRowDto {

    /** 追溯码。 */
    private String traceNo;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 院内药品编码（enrich）。 */
    private String medicationCode;

    /** 药品名称（enrich）。 */
    private String medicationName;

    /** 批号。 */
    private String lotNumber;

    /** 在院状态 code。 */
    private String inHospitalStatusEnum;

    /** 在院状态展示名。 */
    private String inHospitalStatusLabel;

    /** 拆零剩余量。 */
    private BigDecimal remainingSplitQuantity;

    /** 追溯码单位 code。 */
    private String traceUnitCode;

    /** 本库状态 code（LOCATION 范围；否则 null）。 */
    private String locationStockStatusEnum;

    /** 本库状态展示名。 */
    private String locationStockStatusLabel;

    /** 本库净拆零量（LOCATION 范围）。 */
    private BigDecimal locationNetSplitQuantity;

    /** 最近流水时间。 */
    private LocalDateTime lastFlowAt;
}
