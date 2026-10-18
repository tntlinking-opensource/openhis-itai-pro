/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * IF-TRC-RPT-03：单码概览出参。
 */
@Getter
@Setter
public class GetTraceNumberOverviewResponse {

    /** 追溯码。 */
    private String traceNo;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 院内药品编码。 */
    private String medicationCode;

    /** 药品名称。 */
    private String medicationName;

    /** 规格文本。 */
    private String specificationText;

    /** 批号。 */
    private String lotNumber;

    /** 追溯码单位 code。 */
    private String traceUnitCode;

    /** 拆零剩余量。 */
    private BigDecimal remainingSplitQuantity;

    /** 在院状态 code。 */
    private String inHospitalStatusEnum;

    /** 在院状态展示名。 */
    private String inHospitalStatusLabel;
}
