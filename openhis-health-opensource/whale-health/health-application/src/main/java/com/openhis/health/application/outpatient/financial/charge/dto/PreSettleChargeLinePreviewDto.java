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

package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 预结算明细预览 DTO。
 */
@Data
@Accessors(chain = true)
/** 预结算明细预览。 */
@Schema(description = "预结算明细预览")
public class PreSettleChargeLinePreviewDto {
    /**
     * 收费明细主键。
     */
    @Schema(description = "收费明细 ID")
    /** 收费明细 ID（业务字段）。 */
    private Long chargeItemId;
    /**
     * 项目展示名称：与 IF-OPC-03 待收费列表 itemName 同口径（目录 product_name 优先，其次定价 charge_name）。
     */
    @Schema(description = "明细文案（目录名优先，与待收费列表一致）")
    /** 明细文案（目录名优先，与待收费列表一致）（业务字段）。 */
    private String itemLabel;
    /**
     * 本行应收金额。
     */
    @Schema(description = "行金额")
    /** 行金额（业务字段）。 */
    private BigDecimal lineAmount;
}
