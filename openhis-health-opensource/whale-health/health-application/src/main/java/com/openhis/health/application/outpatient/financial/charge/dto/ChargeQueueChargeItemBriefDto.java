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
 * 门诊收费队列行携带的收费明细摘要（与 IF-OPC-03 列表口径一致：待收费/已结算且非挂号锚点）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊收费队列行 — 收费明细摘要")
public class ChargeQueueChargeItemBriefDto {

    @Schema(description = "收费明细 ID")
    private Long chargeItemId;

    @Schema(description = "收费状态持久化码（ChargeItemStatus.code）")
    private String statusCode;

    @Schema(description = "收费状态展示文案")
    private String statusText;

    @Schema(description = "行总金额")
    private BigDecimal totalPrice;

    @Schema(description = "项目名称（目录/定价回落口径与 IF-OPC-03 一致）")
    private String itemLabel;
}
