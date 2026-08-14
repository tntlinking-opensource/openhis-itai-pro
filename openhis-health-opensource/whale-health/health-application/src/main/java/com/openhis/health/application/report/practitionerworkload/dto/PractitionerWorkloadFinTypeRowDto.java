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
 * 工作量汇总 fin_type 行 DTO（IF-RPT-WKL-02；DD-报表中心-进售价口径）。
 *
 * <p>金额列仅售价/进价；本轮不含应收/实收。</p>
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

    /** 售价合计（账单 total_price 加和；行级 6 位）。 */
    @Schema(description = "售价合计")
    private BigDecimal saleAmount;

    /** 进价合计（批号进价包装锚；诊疗无进价不加；行级 6 位）。 */
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmount;
}
