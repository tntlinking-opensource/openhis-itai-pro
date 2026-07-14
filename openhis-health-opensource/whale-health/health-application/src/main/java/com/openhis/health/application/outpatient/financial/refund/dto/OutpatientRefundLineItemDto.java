/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
@Schema(description = "待执行退费明细行")
public class OutpatientRefundLineItemDto {

    /** 收费明细主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费明细主键")
    private Long chargeItemId;

    /** 原支付主单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原支付主单主键。 */
    @Schema(description = "原支付主单主键")
    private Long paymentId;

    /** 收费明细状态 code。 */
    @Schema(description = "收费明细状态编码")
    private String chargeItemStatusEnum;

    /** 收费明细状态展示文案。 */
    @Schema(description = "收费明细状态展示文案")
    private String chargeItemStatusEnumText;

    /** 项目名称展示。 */
    @Schema(description = "项目名称展示")
    private String itemDisplayName;

    /** 行金额。 */
    @Schema(description = "行金额")
    private BigDecimal lineTotalAmount;
}
