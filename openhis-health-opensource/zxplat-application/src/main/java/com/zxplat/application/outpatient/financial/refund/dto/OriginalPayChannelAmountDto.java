/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 原支付单按渠道汇总的实付（退号确认窗原路展示，与逆向单明细同源）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "原支付渠道实付")
public class OriginalPayChannelAmountDto {

    /** 支付渠道编码（{@code fin_payment_rec_detail.pay_enum}）。 */
    @Schema(description = "支付渠道编码")
    private String payChannelCode;

    /**
     * 该渠道实付合计（同一渠道多行相加；只计正数明细）。
     * 退号弹窗用此金额，不用就诊最新预结算个人现金。
     */
    @Schema(description = "该渠道实付合计")
    private BigDecimal amount;
}
