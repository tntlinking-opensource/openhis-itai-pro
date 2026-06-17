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

package com.openhis.health.application.outpatient.financial.refund.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 待执行退费明细行（IF-REF-03）。
 */
@Data
@Accessors(chain = true)
/** 待执行退费明细行。 */
@Schema(description = "待执行退费明细行")
public class OutpatientRefundLineItemDto {

    /** 收费明细主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费明细主键")
    /** 收费明细主键（业务字段）。 */
    private Long chargeItemId;

    /** 原支付主单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原支付主单主键。 */
    @Schema(description = "原支付主单主键")
    /** 原支付主单主键（业务字段）。 */
    private Long paymentId;

    /** 收费明细状态 code。 */
    @Schema(description = "收费明细状态编码")
    /** 收费明细状态编码（业务字段）。 */
    private String chargeItemStatusEnum;

    /** 收费明细状态展示文案。 */
    @Schema(description = "收费明细状态展示文案")
    /** 收费明细状态展示文案（业务字段）。 */
    private String chargeItemStatusEnumText;

    /** 项目名称展示。 */
    @Schema(description = "项目名称展示")
    /** 项目名称展示（业务字段）。 */
    private String itemDisplayName;

    /** 行金额。 */
    @Schema(description = "行金额")
    /** 行金额（业务字段）。 */
    private BigDecimal lineTotalAmount;
}
