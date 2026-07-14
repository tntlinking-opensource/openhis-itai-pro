/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 工作量汇总 fin_type 行 DTO（IF-RPT-WKL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "工作量汇总 fin_type 行")
public class PractitionerWorkloadFinTypeRowDto {

    /** 财务类型码 fin_type_code。 */
    @Schema(description = "财务类型码 fin_type_code")
    private String finTypeCode;

    /** 财务类型展示名。 */
    @Schema(description = "财务类型展示名")
    private String finTypeDisplay;

    /** 应收金额。 */
    @Schema(description = "应收金额")
    private BigDecimal receivableAmount;

    /** 实收金额。 */
    @Schema(description = "实收金额")
    private BigDecimal actualReceivedAmount;

    /** 成本金额。 */
    @Schema(description = "成本金额")
    private BigDecimal costAmount;

    /** 净收入。 */
    @Schema(description = "净收入")
    private BigDecimal netIncomeAmount;
}
