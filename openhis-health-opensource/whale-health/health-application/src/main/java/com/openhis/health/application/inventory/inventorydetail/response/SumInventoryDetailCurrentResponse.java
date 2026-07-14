/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 现库底部汇总出参（IF-INV-DTL-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "现库库存明细汇总 IF-INV-DTL-03")
public class SumInventoryDetailCurrentResponse {
    /** 进价链行总额之和（跳过 null）。 */
    @Schema(description = "进价链行总额之和（跳过 null）")
    private BigDecimal sumLineCostTotal;

    /** 售价链行总额之和（跳过 null）。 */
    @Schema(description = "售价链行总额之和（跳过 null）")
    private BigDecimal sumLinePriceTotal;

    /** 是否触发上限截断。 */
    @Schema(description = "是否触发上限截断")
    private Boolean truncated;
}
