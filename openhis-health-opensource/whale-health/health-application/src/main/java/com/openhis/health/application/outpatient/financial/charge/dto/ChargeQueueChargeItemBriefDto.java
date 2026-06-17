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
/** 门诊收费队列行 — 收费明细摘要。 */
@Schema(description = "门诊收费队列行 — 收费明细摘要")
public class ChargeQueueChargeItemBriefDto {

    @Schema(description = "收费明细 ID")
    /** 收费明细 ID（业务字段）。 */
    private Long chargeItemId;

    /** 收费状态持久化码。 */
    @Schema(description = "收费状态持久化码（ChargeItemStatus.code）")
    /** 收费状态持久化码（ChargeItemStatus.code）（业务字段）。 */
    private String statusCode;

    /** 收费状态展示文案。 */
    @Schema(description = "收费状态展示文案")
    /** 收费状态展示文案（业务字段）。 */
    private String statusText;

    /** 行总金额。 */
    @Schema(description = "行总金额")
    /** 行总金额（业务字段）。 */
    private BigDecimal totalPrice;

    /** 项目名称。 */
    @Schema(description = "项目名称（目录/定价回落口径与 IF-OPC-03 一致）")
    /** 项目名称（目录/定价回落口径与 IF-OPC-03 一致）（业务字段）。 */
    private String itemLabel;
}
