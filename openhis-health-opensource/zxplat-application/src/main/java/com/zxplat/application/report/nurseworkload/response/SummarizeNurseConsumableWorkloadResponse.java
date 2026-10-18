/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.response;

import com.zxplat.application.report.nurseworkload.dto.NurseConsumableSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 护士耗材使用汇总查询出参（IF-RPT-NWL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士耗材使用汇总 IF-RPT-NWL-04")
public class SummarizeNurseConsumableWorkloadResponse {

    /** 汇总行。 */
    @Schema(description = "汇总行")
    private List<NurseConsumableSummaryRowDto> rows;

    /** 合计数量。 */
    @Schema(description = "合计数量")
    private BigDecimal totalQuantity;

    /** 合计售价。 */
    @Schema(description = "合计售价")
    private BigDecimal totalSaleAmount;

    /** 合计优惠（仅命中折扣配置的行）。 */
    @Schema(description = "合计优惠")
    private BigDecimal totalDiscountAmount;

    /** 合计实收（折后应收）。 */
    @Schema(description = "合计实收")
    private BigDecimal totalActualReceivedAmount;

    /** 合计进价。 */
    @Schema(description = "合计进价")
    private BigDecimal totalPurchaseAmount;
}
