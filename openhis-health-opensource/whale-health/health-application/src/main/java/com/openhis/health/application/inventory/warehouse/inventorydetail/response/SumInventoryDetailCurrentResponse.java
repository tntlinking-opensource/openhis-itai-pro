/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.inventorydetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 现库底部汇总出参（IF-INV-DTL-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 现库库存明细汇总 IF-INV-DTL-03。 */
@Schema(description = "现库库存明细汇总 IF-INV-DTL-03")
public class SumInventoryDetailCurrentResponse {

    @Schema(description = "进价链行总额之和（跳过 null）")
    private BigDecimal sumLineCostTotal;

    /** 售价链行总额之和。 */
    @Schema(description = "售价链行总额之和（跳过 null）")
    private BigDecimal sumLinePriceTotal;

    /** 是否触发上限截断。 */
    @Schema(description = "是否触发上限截断")
    private Boolean truncated;
}
