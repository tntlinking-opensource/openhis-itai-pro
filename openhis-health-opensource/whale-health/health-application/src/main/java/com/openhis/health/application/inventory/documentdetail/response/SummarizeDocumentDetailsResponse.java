/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 单据详情合计出参（IF-INV-DDC-{域}-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情合计 IF-INV-DDC-03")
public class SummarizeDocumentDetailsResponse {

    @Schema(description = "金额合计 totalPriceSum")
    /** 金额合计 totalPriceSum。 */
    private BigDecimal totalPriceSum;

    @Schema(description = "展示数量合计 itemQuantitySum")
    /** 展示数量合计 itemQuantitySum。 */
    private BigDecimal itemQuantitySum;

    @Schema(description = "命中行数 rowCount")
    /** 命中行数 rowCount。 */
    private long rowCount;
}
