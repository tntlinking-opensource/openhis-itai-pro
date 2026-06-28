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

package com.openhis.health.application.finance.priceadjustment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 调价批号明细 DTO（物流多批号 / 诊疗一对一调前价行）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价批号明细")
public class PriceAdjustmentDetailDto {

    /** 子表主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "子表主键")
    private Long id;

    /** 批号；诊疗占位为 {@code -}。 */
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

    /** 进价盈亏（提交后固化）。 */
    @Schema(description = "进价盈亏")
    private BigDecimal purchaseImpact;

    /** 售价盈亏（提交后固化）。 */
    @Schema(description = "售价盈亏")
    private BigDecimal sellingImpact;
}
