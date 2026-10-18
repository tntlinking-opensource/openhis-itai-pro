/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊收费列表折后价试算行（IF-OPC-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊收费列表折后价试算行")
public class PreviewOutpatientChargeDiscountLineDto {

    /**
     * 收费明细主键。
     */
    @Schema(description = "收费明细主键")
    private Long chargeItemId;

    /**
     * 原价（totalPrice / baseAmount）。
     */
    @Schema(description = "原价")
    private BigDecimal originalAmount;

    /**
     * 折后价（结算 2 位）；未打折等于原价刻度。
     */
    @Schema(description = "折后价")
    private BigDecimal discountedAmount;

    /**
     * 优惠额（原价−折后）；未打折为 0。
     */
    @Schema(description = "优惠额")
    private BigDecimal discountGap;

    /**
     * 本行有效折扣率 (0,1]；未打折为空。
     */
    @Schema(description = "有效折扣率")
    private BigDecimal discountRate;

    /**
     * 本行是否实际参与打折。
     */
    @Schema(description = "是否打折")
    private Boolean discounted;
}
