/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 工作量汇总 fin_type 行 DTO（IF-RPT-WKL-02；DD-报表中心-进售价口径）。
 *
 * <p>金额列含售价/优惠/实收/进价。</p>
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

    /** 优惠合计（售价 − 折后应收；未命中折扣配置为 0）。 */
    @Schema(description = "优惠合计")
    private BigDecimal discountAmount;

    /** 实收合计（折后应收；未打折等于售价）。 */
    @Schema(description = "实收合计")
    private BigDecimal actualReceivedAmount;

    /** 进价合计（批号进价包装锚；诊疗无进价不加；行级 6 位）。 */
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmount;
}
