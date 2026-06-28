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

package com.openhis.health.application.finance.priceadjustment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 保存调价草稿批号明细入参（IF-PRC-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批号明细")
public class SavePriceAdjustmentDraftDetailLineRequest {

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 快照数量。 */
    @Schema(description = "快照数量")
    private BigDecimal quantity;

    /** 调前进价。 */
    @Schema(description = "调前进价")
    private BigDecimal oldPurchaseAmount;

    /** 调前售价。 */
    @Schema(description = "调前售价")
    private BigDecimal oldSellingAmount;
}
