/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 备份库存底部汇总出参（IF-INV-DTL-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "备份库存明细汇总 IF-INV-DTL-05")
public class SumInventoryDetailBackupResponse {
    /** 进价行总额之和。 */
    @Schema(description = "进价行总额之和")
    private BigDecimal sumLineCostTotal;

    /** 售价行总额之和。 */
    @Schema(description = "售价行总额之和")
    private BigDecimal sumLinePriceTotal;

    /** 是否触发上限截断。 */
    @Schema(description = "是否触发上限截断")
    private Boolean truncated;
}
